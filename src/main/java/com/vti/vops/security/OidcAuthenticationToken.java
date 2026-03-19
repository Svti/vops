package com.vti.vops.security;

import com.vti.vops.entity.User;
import org.apache.shiro.authc.AuthenticationToken;

/**
 * OIDC 登录成功后向 Shiro 提交的 Token，Realm 直接信任并建立会话
 */
public class OidcAuthenticationToken implements AuthenticationToken {

    private static final long serialVersionUID = 1L;
    
	private final User user;

    public OidcAuthenticationToken(User user) {
        this.user = user;
    }

    @Override
    public Object getPrincipal() {
        return user.getUsername();
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    public User getUser() {
        return user;
    }
}
