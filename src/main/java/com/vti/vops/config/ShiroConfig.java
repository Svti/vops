package com.vti.vops.config;

import com.vti.vops.security.OidcAwareCredentialsMatcher;
import com.vti.vops.security.RequireRoleFilter;
import com.vti.vops.security.VopsRealm;
import com.vti.vops.service.IUserAuthService;
import com.vti.vops.service.IUserService;
import org.apache.shiro.authc.credential.HashedCredentialsMatcher;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.spring.web.config.DefaultShiroFilterChainDefinition;
import org.apache.shiro.spring.web.config.ShiroFilterChainDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Shiro 配置：Realm、凭证匹配器（SHA256+salt）、过滤链；以及供启动时 setDefaultProperties 使用的默认属性。
 * SecurityManager 由 shiro-spring-boot 自动配置。
 */
@Configuration
public class ShiroConfig {

    /**
     * 匿名路径唯一配置：与 shiroFilterChainDefinition 中 anon 一致。
     * 以 "/" 结尾表示前缀匹配（如 /login/oidc/、/static/），否则为精确匹配。
     */
    private static final List<String> ANON_PATH_PATTERNS = Arrays.asList(
            "/login",
            "/login/oidc/",
            "/no-permission",
            "/logout",
            "/static/",
            "/favicon.svg",
            "/logo.svg",
            "/logo-dark.svg",
            "/unauthorized"
    );

    public static boolean isAnonPath(String path) {
        if (path == null) return true;
        for (String p : ANON_PATH_PATTERNS) {
            if (p.endsWith("/")) {
                String prefix = p.substring(0, p.length() - 1);
                if (path.startsWith(p) || path.equals(prefix)) return true;
            } else {
                if (path.equals(p)) return true;
            }
        }
        return false;
    }

    /** 极少修改的 shiro 默认属性，供 VopsApplication 合并到默认配置。 */
    public static Map<String, Object> defaultProperties() {
        return Map.of(
                "shiro.loginUrl", "/login",
                "shiro.unauthorizedUrl", "/unauthorized",
                "shiro.successUrl", "/",
                "shiro.sessionTimeout", 1800000
        );
    }

    @Bean
    public Realm realm(IUserService userService, IUserAuthService userAuthService) {
        VopsRealm realm = new VopsRealm(userService, userAuthService);
        HashedCredentialsMatcher hashedMatcher = new HashedCredentialsMatcher("SHA-256");
        hashedMatcher.setHashIterations(1);
        hashedMatcher.setStoredCredentialsHexEncoded(false);
        realm.setCredentialsMatcher(new OidcAwareCredentialsMatcher(hashedMatcher));
        return realm;
    }

    @Bean(name = "requireRole")
    public RequireRoleFilter requireRoleFilter(IUserAuthService userAuthService) {
        RequireRoleFilter filter = new RequireRoleFilter();
        filter.setUserAuthService(userAuthService);
        filter.setAnonPathPredicate(ShiroConfig::isAnonPath);
        filter.setLoginUrl("/login");
        return filter;
    }

    @Bean
    public ShiroFilterChainDefinition shiroFilterChainDefinition() {
        DefaultShiroFilterChainDefinition chain = new DefaultShiroFilterChainDefinition();
        for (String p : ANON_PATH_PATTERNS) {
            if (p.endsWith("/")) {
                String base = p.substring(0, p.length() - 1);
                chain.addPathDefinition(base, "anon");
                chain.addPathDefinition(p + "**", "anon");
            } else {
                chain.addPathDefinition(p, "anon");
            }
        }
        chain.addPathDefinition("/ws/**", "authc");
        chain.addPathDefinition("/**", "requireRole");
        return chain;
    }
}
