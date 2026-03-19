package com.vti.vops.service;

import com.vti.vops.security.OidcUserInfo;

/**
 * OIDC 客户端服务接口：发现、授权 URL、用 code 换 token、解析用户信息
 */
public interface IOidcClientService {

    String buildAuthorizationUrl(String redirectUriAbsolute, String state);

    OidcUserInfo exchangeCodeAndGetUserInfo(String code, String redirectUriAbsolute);
}
