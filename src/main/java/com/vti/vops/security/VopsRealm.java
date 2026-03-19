package com.vti.vops.security;

import com.vti.vops.entity.User;
import com.vti.vops.service.IUserAuthService;
import com.vti.vops.service.IUserService;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.authc.*;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.util.ByteSource;

import java.util.List;

/**
 * Shiro Realm：支持表单登录（用户名+密码）与 OIDC Token
 */
@RequiredArgsConstructor
public class VopsRealm extends AuthorizingRealm {

    private final IUserService userService;
    private final IUserAuthService userAuthService;

    @Override
    public boolean supports(AuthenticationToken token) {
        return token instanceof UsernamePasswordToken || token instanceof OidcAuthenticationToken;
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        if (token instanceof OidcAuthenticationToken) {
            User user = ((OidcAuthenticationToken) token).getUser();
            return new SimpleAuthenticationInfo(user, null, getName());
        }
        UsernamePasswordToken up = (UsernamePasswordToken) token;
        String username = up.getUsername();
        User user = userService.findByUsername(username).orElseThrow(() -> new UnknownAccountException("用户不存在"));
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new DisabledAccountException("账号已禁用");
        }
        ByteSource salt = ByteSource.Util.bytes(user.getSalt() != null ? user.getSalt() : "");
        return new SimpleAuthenticationInfo(user, user.getPassword(), salt, getName());
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        Object p = principals.getPrimaryPrincipal();
        Long userId = null;
        if (p instanceof User) {
            userId = ((User) p).getId();
        } else if (p instanceof String) {
            userId = userService.findByUsername((String) p).map(User::getId).orElse(null);
        }
        if (userId == null) return new SimpleAuthorizationInfo();
        SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();
        List<String> roles = userAuthService.getRoleCodesByUserId(userId);
        List<String> perms = userAuthService.getPermCodesByUserId(userId);
        info.setRoles(roles != null ? new java.util.HashSet<>(roles) : new java.util.HashSet<>());
        info.setStringPermissions(perms != null ? new java.util.HashSet<>(perms) : new java.util.HashSet<>());
        return info;
    }
}
