package com.vti.vops.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.vti.vops.entity.Host;
import com.vti.vops.entity.HostGroup;
import com.vti.vops.entity.Permission;
import com.vti.vops.entity.RolePermission;
import com.vti.vops.entity.UserRole;
import com.vti.vops.mapper.HostGroupMapper;
import com.vti.vops.mapper.HostMapper;
import com.vti.vops.mapper.PermissionMapper;
import com.vti.vops.mapper.RolePermissionMapper;
import com.vti.vops.mapper.UserRoleMapper;
import com.vti.vops.service.IHostService;
import com.vti.vops.service.ISshKeyService;
import com.vti.vops.util.EncryptUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 主机服务实现：CRUD、主机级权限、SSH 凭证 AES 加密存储
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HostServiceImpl extends ServiceImpl<HostMapper, Host> implements IHostService {

    private final HostGroupMapper hostGroupMapper;
    private final UserRoleMapper userRoleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PermissionMapper permissionMapper;
    private final EncryptUtil encryptUtil;
    private final ISshKeyService sshKeyService;

    @Override
    public Map<Long, List<Host>> listHostsGroupedBySshKeyIds(Collection<Long> sshKeyIds) {
        if (sshKeyIds == null || sshKeyIds.isEmpty()) return Collections.emptyMap();
        List<Host> hosts = list(new LambdaQueryWrapper<Host>()
                .in(Host::getSshKeyId, sshKeyIds)
                .eq(Host::getStatus, 1));
        return hosts.stream().filter(h -> h.getSshKeyId() != null).collect(Collectors.groupingBy(Host::getSshKeyId));
    }

    @Override
    public List<Host> listByUserIdWithPermission(Long userId) {
        return listByUserIdWithPermission(userId, false);
    }

    private List<Host> listByUserIdWithPermission(Long userId, boolean includeDisabled) {
        List<Long> roleIds = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId)
        ).stream().map(UserRole::getRoleId).distinct().toList();
        if (roleIds.isEmpty()) return Collections.emptyList();

        List<Long> permIds = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermission>().in(RolePermission::getRoleId, roleIds)
        ).stream().map(RolePermission::getPermissionId).distinct().toList();
        if (permIds.isEmpty()) return Collections.emptyList();

        List<Permission> perms = permissionMapper.selectList(
                new LambdaQueryWrapper<Permission>().in(Permission::getId, permIds)
                        .eq(Permission::getResourceType, "host")
        );
        LambdaQueryWrapper<Host> q = new LambdaQueryWrapper<>();
        boolean hasAll = perms.stream().anyMatch(p -> p.getResourceId() == null);
        if (!hasAll) {
            List<Long> hostIds = perms.stream().map(Permission::getResourceId).filter(Objects::nonNull).distinct().toList();
            if (hostIds.isEmpty()) return Collections.emptyList();
            q.in(Host::getId, hostIds);
        }
        if (!includeDisabled) q.eq(Host::getStatus, 1);
        List<Host> result = list(q);
        return result != null ? result : Collections.emptyList();
    }

    @Override
    public Host getByIdWithDecryptedCredential(Long id) {
        Host h = getById(id);
        if (h == null) return null;
        if (h.getSshKeyId() != null) {
            String content = sshKeyService.getDecryptedContent(h.getSshKeyId());
            if (content == null || content.isBlank()) {
                h.setCredential(null);
            } else {
                h.setCredential(content);
            }
        } else {
            decryptCredential(h);
        }
        return h;
    }

    private Host decryptCredential(Host h) {
        if (h.getCredential() != null && !h.getCredential().isEmpty()) {
            String dec = encryptUtil.decrypt(h.getCredential());
            if (dec != null) {
                h.setCredential(dec);
            } else {
                log.warn("Host id={} credential decrypt failed (wrong key or corrupt data), using null", h.getId());
                h.setCredential(null);
            }
        }
        return h;
    }

    @Override
    public List<Host> listForUser(Long userId) {
        List<Host> list = listByUserIdWithPermission(userId, false);
        list.forEach(h -> h.setCredential(null));
        return list;
    }

    @Override
    public List<Host> listForUserIncludeDisabled(Long userId) {
        List<Host> list = listByUserIdWithPermission(userId, true);
        list.forEach(h -> h.setCredential(null));
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(Host host) {
        if (host.getPort() == null) host.setPort(22);
        if (host.getStatus() == null) host.setStatus(1);
        if (host.getAuthType() == null) host.setAuthType("password");
        if (host.getId() != null) {
            Host existing = getById(host.getId());
            if (existing != null) {
                if (host.getSshKeyId() != null) {
                    host.setCredential(null);
                } else if (host.getCredential() == null || host.getCredential().isEmpty()) {
                    host.setCredential(existing.getCredential());
                }
            }
        }
        if (host.getSshKeyId() != null) {
            host.setCredential(null);
        }
        if (host.getCredential() != null && !host.getCredential().isEmpty() && !encryptUtil.isEncrypted(host.getCredential())) {
            host.setCredential(encryptUtil.encrypt(host.getCredential()));
        }
        if (host.getId() != null && host.getSshKeyId() == null) {
            update(new LambdaUpdateWrapper<Host>().eq(Host::getId, host.getId()).set(Host::getSshKeyId, null));
        }
        return host.getId() == null ? super.save(host) : updateById(host);
    }

    @Override
    public List<HostGroup> listGroups() {
        return hostGroupMapper.selectList(new LambdaQueryWrapper<HostGroup>().orderByAsc(HostGroup::getId));
    }

    @Override
    public long countByGroupId(Long groupId) {
        if (groupId == null) return 0;
        return count(new LambdaQueryWrapper<Host>().eq(Host::getGroupId, groupId));
    }

    @Override
    public List<Long> listHostIdsByGroupIds(List<Long> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) return Collections.emptyList();
        return list(new LambdaQueryWrapper<Host>()
                        .in(Host::getGroupId, groupIds)
                        .eq(Host::getStatus, 1))
                .stream()
                .map(Host::getId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveGroup(HostGroup group) {
        if (group == null) return;
        if (group.getId() == null) {
            hostGroupMapper.insert(group);
        } else {
            hostGroupMapper.updateById(group);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long getOrCreateGroupByName(String name) {
        if (name == null || name.isBlank()) return null;
        String trimmed = name.trim();
        HostGroup existing = hostGroupMapper.selectOne(
                new LambdaQueryWrapper<HostGroup>().eq(HostGroup::getName, trimmed).last("LIMIT 1"));
        if (existing != null && existing.getId() != null) return existing.getId();
        HostGroup group = new HostGroup();
        group.setName(trimmed);
        hostGroupMapper.insert(group);
        return group.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeGroupById(Long id) {
        if (id == null) return;
        if (countByGroupId(id) > 0) {
            throw new IllegalStateException("该分组下还有主机，请先将主机移出分组或删除主机后再删除分组");
        }
        hostGroupMapper.deleteById(id);
    }

    @Override
    public int batchSetGroupIds(List<Long> hostIds, Long groupId) {
        if (hostIds == null || hostIds.isEmpty()) return 0;
        LambdaUpdateWrapper<Host> w = new LambdaUpdateWrapper<Host>()
                .in(Host::getId, hostIds)
                .set(Host::getGroupId, groupId);
        return (int) getBaseMapper().update(null, w);
    }

    @Override
    public int batchSetAuth(List<Long> hostIds, String authType, Long sshKeyId, String plainPassword) {
        if (hostIds == null || hostIds.isEmpty()) return 0;
        String at = authType != null ? authType : "password";
        LambdaUpdateWrapper<Host> w = new LambdaUpdateWrapper<Host>()
                .in(Host::getId, hostIds)
                .set(Host::getAuthType, at)
                .set(Host::getSshKeyId, sshKeyId);
        if ("password".equals(at) && plainPassword != null && !plainPassword.isBlank()) {
            w.set(Host::getCredential, encryptUtil.encrypt(plainPassword));
        }
        return (int) getBaseMapper().update(null, w);
    }

    @Override
    public List<Host> listAllEnabled() {
        return list(new LambdaQueryWrapper<Host>().eq(Host::getStatus, 1));
    }
}
