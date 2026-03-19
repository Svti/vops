package com.vti.vops.security;

import lombok.Data;

/**
 * OIDC UserInfo 或 id_token 中的用户信息
 */
@Data
public class OidcUserInfo {

    private String sub;
    private String email;
    private String name;
    private String preferredUsername;
}
