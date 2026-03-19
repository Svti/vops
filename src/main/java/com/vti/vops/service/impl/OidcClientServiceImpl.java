package com.vti.vops.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.vti.vops.config.OidcProperties;
import com.vti.vops.security.OidcUserInfo;
import com.vti.vops.service.IOidcClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;

/**
 * OIDC 客户端：发现、授权 URL、用 code 换 token、解析用户信息
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OidcClientServiceImpl implements IOidcClientService {

    private final OidcProperties oidcProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    @PostConstruct
    public void init() {
        if (!oidcProperties.isEnabled()) return;
        try {
            String issuer = oidcProperties.getIssuerUri().replaceAll("/$", "");
            String url = issuer + "/.well-known/openid-configuration";
            ResponseEntity<JsonNode> resp = restTemplate.getForEntity(url, JsonNode.class);
            JsonNode body = resp.getBody();
            if (body != null) {
                oidcProperties.setAuthorizationEndpoint(body.path("authorization_endpoint").asText());
                oidcProperties.setTokenEndpoint(body.path("token_endpoint").asText());
                oidcProperties.setUserinfoEndpoint(body.has("userinfo_endpoint") ? body.path("userinfo_endpoint").asText() : null);
                log.info("OIDC discovery loaded: auth={}", oidcProperties.getAuthorizationEndpoint());
            }
        } catch (Exception e) {
            log.warn("OIDC discovery failed: {}", e.getMessage());
        }
    }

    @Override
    public String buildAuthorizationUrl(String redirectUriAbsolute, String state) {
        String issuer = oidcProperties.getIssuerUri().replaceAll("/$", "");
        if (oidcProperties.getAuthorizationEndpoint() == null) {
            oidcProperties.setAuthorizationEndpoint(issuer + "/oauth2/authorize");
            oidcProperties.setTokenEndpoint(issuer + "/oauth2/token");
        }
        String auth = oidcProperties.getAuthorizationEndpoint();
        return auth + "?response_type=code&client_id=" + encode(oidcProperties.getClientId())
                + "&redirect_uri=" + encode(redirectUriAbsolute)
                + "&scope=" + encode(oidcProperties.getScope())
                + "&state=" + encode(state);
    }

    @Override
    public OidcUserInfo exchangeCodeAndGetUserInfo(String code, String redirectUriAbsolute) {
        return exchangeCodeAndGetUserInfoFromIdToken(code, redirectUriAbsolute);
    }

    public TokenResponse exchangeCodeForTokenResponse(String code, String redirectUriAbsolute) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(oidcProperties.getClientId(), oidcProperties.getClientSecret());
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("code", code);
        params.add("redirect_uri", redirectUriAbsolute);
        ResponseEntity<JsonNode> resp = restTemplate.postForEntity(
                oidcProperties.getTokenEndpoint(),
                new HttpEntity<>(params, headers),
                JsonNode.class
        );
        JsonNode body = resp.getBody();
        if (body == null) return null;
        String idToken = body.has("id_token") ? body.path("id_token").asText() : null;
        String accessToken = body.has("access_token") ? body.path("access_token").asText() : null;
        return new TokenResponse(idToken, accessToken);
    }

    private OidcUserInfo fetchUserInfo(String accessToken) {
        String userinfoUrl = oidcProperties.getUserinfoEndpoint();
        if (userinfoUrl == null || userinfoUrl.isEmpty()) return null;
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        try {
            ResponseEntity<JsonNode> resp = restTemplate.exchange(
                    userinfoUrl,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    JsonNode.class
            );
            JsonNode n = resp.getBody();
            if (n == null) return null;
            OidcUserInfo info = new OidcUserInfo();
            info.setSub(n.has("sub") ? n.path("sub").asText() : null);
            info.setEmail(n.has("email") ? n.path("email").asText() : null);
            info.setName(n.has("name") ? n.path("name").asText() : null);
            info.setPreferredUsername(n.has("preferred_username") ? n.path("preferred_username").asText() : null);
            return info;
        } catch (Exception e) {
            log.warn("OIDC userinfo failed: {}", e.getMessage());
            return null;
        }
    }

    public OidcUserInfo parseIdToken(String idToken) {
        try {
            SignedJWT jwt = SignedJWT.parse(idToken);
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            OidcUserInfo info = new OidcUserInfo();
            info.setSub(claims.getSubject());
            info.setEmail(claims.getStringClaim("email"));
            info.setName(claims.getStringClaim("name"));
            info.setPreferredUsername(claims.getStringClaim("preferred_username"));
            return info;
        } catch (Exception e) {
            log.warn("Parse id_token failed: {}", e.getMessage());
            return null;
        }
    }

    public OidcUserInfo exchangeCodeAndGetUserInfoFromIdToken(String code, String redirectUriAbsolute) {
        TokenResponse tr = exchangeCodeForTokenResponse(code, redirectUriAbsolute);
        if (tr == null) return null;
        if (tr.idToken != null) {
            OidcUserInfo fromIdToken = parseIdToken(tr.idToken);
            if (fromIdToken != null) return fromIdToken;
        }
        if (tr.accessToken != null) {
            return fetchUserInfo(tr.accessToken);
        }
        return null;
    }

    private static String encode(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    public static class TokenResponse {
        public final String idToken;
        public final String accessToken;
        public TokenResponse(String idToken, String accessToken) {
            this.idToken = idToken;
            this.accessToken = accessToken;
        }
    }
}
