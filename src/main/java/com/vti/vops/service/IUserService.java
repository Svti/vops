package com.vti.vops.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.vti.vops.entity.User;
import com.vti.vops.security.OidcUserInfo;

import java.util.List;
import java.util.Optional;

/**
 * 用户服务接口：CRUD 与 OIDC 关联
 */
public interface IUserService extends IService<User> {

    Optional<User> findByUsername(String username);

    Optional<User> findByOidcSub(String oidcSub);

    User findOrCreateByOidc(OidcUserInfo info);

    /**
     * 修改当前用户密码（仅本地用户；需校验旧密码）
     */
    void updatePassword(Long userId, String oldPassword, String newPassword);

    /** 用户管理：获取用户已选角色 ID 列表 */
    List<Long> getRoleIdsByUserId(Long userId);

    /** 保存用户并更新角色（新增时 plainPassword 必填，编辑时可为 null 表示不改密码） */
    void saveUserWithRoles(User user, String plainPassword, List<Long> roleIds);

    /** 管理员重置用户密码（仅本地用户） */
    void resetPasswordByAdmin(Long userId, String newPassword);
}
