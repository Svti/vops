package com.vti.vops.security;

import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.credential.CredentialsMatcher;
import org.apache.shiro.authc.credential.HashedCredentialsMatcher;

/**
 * 凭证匹配器：OIDC 登录（无密码）时直接通过；表单登录时委托 HashedCredentialsMatcher 校验密码。
 */
public class OidcAwareCredentialsMatcher implements CredentialsMatcher {

    private final HashedCredentialsMatcher hashedMatcher;

    public OidcAwareCredentialsMatcher(HashedCredentialsMatcher hashedMatcher) {
        this.hashedMatcher = hashedMatcher;
    }

    @Override
    public boolean doCredentialsMatch(AuthenticationToken token, AuthenticationInfo info) {
        if (token instanceof OidcAuthenticationToken) {
            return true;
        }
        Object tokenCredentials = token != null ? token.getCredentials() : null;
        Object infoCredentials = info != null ? info.getCredentials() : null;
        if (tokenCredentials == null && infoCredentials == null) {
            return true;
        }
        return hashedMatcher.doCredentialsMatch(token, info);
    }
}
