package com.vti.vops.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vti.vops.entity.Permission;
import com.vti.vops.entity.Role;
import com.vti.vops.entity.RolePermission;
import com.vti.vops.entity.UserRole;
import com.vti.vops.mapper.PermissionMapper;
import com.vti.vops.mapper.RoleMapper;
import com.vti.vops.mapper.RolePermissionMapper;
import com.vti.vops.mapper.UserRoleMapper;
import com.vti.vops.service.IUserAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户认证授权查询，全部使用 MyBatis-Plus Lambda 查询
 */
@Service
@RequiredArgsConstructor
public class UserAuthServiceImpl implements IUserAuthService {

    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PermissionMapper permissionMapper;

    @Override
    public List<String> getRoleCodesByUserId(Long userId) {
        List<Long> roleIds = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId)
        ).stream().map(UserRole::getRoleId).distinct().toList();
        if (roleIds.isEmpty()) return Collections.emptyList();
        return roleMapper.selectList(
                new LambdaQueryWrapper<Role>().in(Role::getId, roleIds)
        ).stream().map(Role::getRoleCode).collect(Collectors.toList());
    }

    @Override
    public List<String> getPermCodesByUserId(Long userId) {
        List<Long> roleIds = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId)
        ).stream().map(UserRole::getRoleId).distinct().toList();
        if (roleIds.isEmpty()) return Collections.emptyList();
        List<Long> permIds = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermission>().in(RolePermission::getRoleId, roleIds)
        ).stream().map(RolePermission::getPermissionId).distinct().toList();
        if (permIds.isEmpty()) return Collections.emptyList();
        return permissionMapper.selectList(
                new LambdaQueryWrapper<Permission>().in(Permission::getId, permIds)
        ).stream().map(Permission::getPermCode).distinct().collect(Collectors.toList());
    }
}
