package com.vti.vops.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OIDC 登录配置
 */
@Data
@ConfigurationProperties(prefix = "oidc")
public class OidcProperties {

    private boolean enabled = false;
    /** IdP 发行者地址，如 https://idp.example.com/ */
    private String issuerUri;
    private String clientId;
    private String clientSecret;
    /** 回调路径或完整 URL，如 /login/oidc/callback */
    private String redirectUri;
    private String scope = "openid profile email";
    /** 登录成功跳转，默认 / */
    private String successUrl = "/";

    /** 发现文档缓存的端点（运行时从 issuer 发现） */
    private transient String authorizationEndpoint;
    private transient String tokenEndpoint;
    private transient String userinfoEndpoint;
}
