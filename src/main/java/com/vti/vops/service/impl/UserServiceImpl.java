package com.vti.vops.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.vti.vops.entity.User;
import com.vti.vops.entity.UserRole;
import com.vti.vops.mapper.UserMapper;
import com.vti.vops.mapper.UserRoleMapper;
import com.vti.vops.service.IUserService;
import com.vti.vops.security.OidcUserInfo;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.crypto.hash.SimpleHash;
import org.apache.shiro.util.ByteSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 用户服务实现：CRUD 与 OIDC 关联
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private final UserRoleMapper userRoleMapper;

    @Override
    public List<Long> getRoleIdsByUserId(Long userId) {
        if (userId == null) return Collections.emptyList();
        return userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId)
        ).stream().map(UserRole::getRoleId).distinct().toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveUserWithRoles(User user, String plainPassword, List<Long> roleIds) {
        if (user.getId() != null) {
            updateById(user);
            if (plainPassword != null && !plainPassword.isBlank()) {
                String newSalt = UUID.randomUUID().toString().replace("-", "");
                String hashed = new SimpleHash("SHA-256", plainPassword, ByteSource.Util.bytes(newSalt), 1).toBase64();
                user.setSalt(newSalt);
                user.setPassword(hashed);
                updateById(user);
            }
        } else {
            if (plainPassword == null || plainPassword.isBlank()) {
                throw new IllegalArgumentException("新建用户必须设置密码");
            }
            String newSalt = UUID.randomUUID().toString().replace("-", "");
            String hashed = new SimpleHash("SHA-256", plainPassword, ByteSource.Util.bytes(newSalt), 1).toBase64();
            user.setSalt(newSalt);
            user.setPassword(hashed);
            if (user.getStatus() == null) user.setStatus(1);
            save(user);
        }
        if (roleIds != null && user.getId() != null) {
            userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, user.getId()));
            for (Long roleId : roleIds) {
                if (roleId == null) continue;
                UserRole ur = new UserRole();
                ur.setUserId(user.getId());
                ur.setRoleId(roleId);
                userRoleMapper.insert(ur);
            }
        }
    }

    @Override
    public void resetPasswordByAdmin(Long userId, String newPassword) {
        User user = getById(userId);
        if (user == null) throw new IllegalArgumentException("用户不存在");
        if (user.getOidcSub() != null && !user.getOidcSub().isBlank()) {
            throw new IllegalStateException("OIDC 用户无法在此重置密码");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("新密码至少 6 位");
        }
        String newSalt = UUID.randomUUID().toString().replace("-", "");
        String hashed = new SimpleHash("SHA-256", newPassword, ByteSource.Util.bytes(newSalt), 1).toBase64();
        user.setSalt(newSalt);
        user.setPassword(hashed);
        updateById(user);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(
                getOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username))
        );
    }

    @Override
    public Optional<User> findByOidcSub(String oidcSub) {
        if (oidcSub == null || oidcSub.isBlank()) return Optional.empty();
        return Optional.ofNullable(
                getOne(new LambdaQueryWrapper<User>().eq(User::getOidcSub, oidcSub))
        );
    }

    @Override
    public User findOrCreateByOidc(OidcUserInfo info) {
        if (info == null || info.getSub() == null) return null;
        return findByOidcSub(info.getSub()).orElseGet(() -> createUserForOidc(info));
    }

    private User createUserForOidc(OidcUserInfo info) {
        String username = info.getPreferredUsername() != null ? info.getPreferredUsername() : info.getSub();
        if (username != null && findByUsername(username).isPresent()) {
            username = "oidc_" + info.getSub().replaceAll("[^a-zA-Z0-9]", "_");
        }
        if (username == null) username = "oidc_" + UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setUsername(username);
        user.setPassword("");
        user.setSalt("");
        user.setOidcSub(info.getSub());
        user.setEmail(info.getEmail());
        user.setRealName(info.getName());
        user.setStatus(1);
        save(user);
        return user;
    }

    @Override
    public void updatePassword(Long userId, String oldPassword, String newPassword) {
        User user = getById(userId);
        if (user == null) throw new IllegalArgumentException("用户不存在");
        if (user.getOidcSub() != null && !user.getOidcSub().isBlank()) {
            throw new IllegalStateException("OIDC 用户请在外部 IdP 修改密码");
        }
        String salt = user.getSalt() != null ? user.getSalt() : "";
        String stored = user.getPassword() != null ? user.getPassword() : "";
        String hashedOld = new SimpleHash("SHA-256", oldPassword, ByteSource.Util.bytes(salt), 1).toBase64();
        if (!hashedOld.equals(stored)) {
            throw new IllegalArgumentException("原密码错误");
        }
        String newSalt = UUID.randomUUID().toString().replace("-", "");
        String hashedNew = new SimpleHash("SHA-256", newPassword, ByteSource.Util.bytes(newSalt), 1).toBase64();
        user.setSalt(newSalt);
        user.setPassword(hashedNew);
        updateById(user);
    }
}
