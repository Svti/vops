package com.vti.vops.controller;

import com.vti.vops.config.OidcProperties;
import com.vti.vops.entity.User;
import com.vti.vops.security.OidcAuthenticationToken;
import com.vti.vops.security.OidcUserInfo;
import com.vti.vops.service.IAuditLogService;
import com.vti.vops.service.IOidcClientService;
import com.vti.vops.service.IUserAuthService;
import com.vti.vops.service.IUserService;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

import java.util.List;
import java.util.UUID;

/**
 * OIDC 登录：发起授权、回调后建 Shiro 会话
 */
@Controller
@RequestMapping("/login/oidc")
@RequiredArgsConstructor
public class OidcLoginController {

	private static final String SESSION_OIDC_STATE = "oidc_state";

	private final OidcProperties oidcProperties;
	private final IOidcClientService oidcClientService;
	private final IUserService userService;
	private final IUserAuthService userAuthService;
	private final IAuditLogService auditLogService;

	/** 发起 OIDC 登录，重定向到 IdP */
	@GetMapping
	public RedirectView login(HttpServletRequest request, HttpSession session) {
		if (!oidcProperties.isEnabled()) {
			auditLogService.log(null, null, "login_fail", "auth", null, "oidc:oidc_disabled", getClientIp(request),
					request.getHeader("User-Agent"));
			return new RedirectView("/login?error=oidc_disabled", true);
		}
		String state = UUID.randomUUID().toString().replace("-", "");
		session.setAttribute(SESSION_OIDC_STATE, state);
		String redirectUri = oidcProperties.getRedirectUri();
		String authUrl = oidcClientService.buildAuthorizationUrl(redirectUri, state);
		return new RedirectView(authUrl, true);
	}

	/** IdP 回调：用 code 换用户信息，查找或创建用户并登录 */
	@GetMapping("/callback")
	public RedirectView callback(@RequestParam(required = false) String code,
			@RequestParam(required = false) String state, @RequestParam(required = false) String error,
			HttpServletRequest request, HttpSession session) {
		String ip = getClientIp(request);
		String userAgent = request.getHeader("User-Agent");
		if (!oidcProperties.isEnabled()) {
			auditLogService.log(null, null, "login_fail", "auth", null, "oidc:oidc_disabled", ip, userAgent);
			return new RedirectView("/login?error=oidc_disabled", true);
		}
		if (error != null) {
			auditLogService.log(null, null, "login_fail", "auth", null, "oidc:idp_error:" + error, ip, userAgent);
			return new RedirectView("/login?error=" + error, true);
		}
		Object savedState = session.getAttribute(SESSION_OIDC_STATE);
		session.removeAttribute(SESSION_OIDC_STATE);
		if (savedState == null || !savedState.toString().equals(state)) {
			auditLogService.log(null, null, "login_fail", "auth", null, "oidc:invalid_state", ip, userAgent);
			return new RedirectView("/login?error=invalid_state", true);
		}
		if (code == null || code.isBlank()) {
			auditLogService.log(null, null, "login_fail", "auth", null, "oidc:no_code", ip, userAgent);
			return new RedirectView("/login?error=no_code", true);
		}
		String redirectUri = oidcProperties.getRedirectUri();
		OidcUserInfo userInfo = oidcClientService.exchangeCodeAndGetUserInfo(code, redirectUri);
		if (userInfo == null) {
			auditLogService.log(null, null, "login_fail", "auth", null, "oidc:oidc_exchange_failed", ip, userAgent);
			return new RedirectView("/login?error=oidc_exchange_failed", true);
		}
		User user = userService.findOrCreateByOidc(userInfo);
		if (user == null) {
			String name = userInfo.getPreferredUsername() != null ? userInfo.getPreferredUsername() : userInfo.getSub();
			auditLogService.log(null, name, "login_fail", "auth", null, "oidc:user_create_failed", ip, userAgent);
			return new RedirectView("/login?error=user_create_failed", true);
		}
		Subject subject = SecurityUtils.getSubject();
		subject.login(new OidcAuthenticationToken(user));
		List<String> roles = userAuthService.getRoleCodesByUserId(user.getId());
		if (roles == null || roles.isEmpty()) {
			auditLogService.log(user.getId(), user.getUsername(), "login_fail", "auth", null, "oidc:no_role", ip,
					userAgent);
			return new RedirectView("/no-permission", true);
		}
		auditLogService.log(user.getId(), user.getUsername(), "login", "auth", null, "oidc", ip, userAgent);
		String successUrl = oidcProperties.getSuccessUrl() != null ? oidcProperties.getSuccessUrl() : "/";
		return new RedirectView(successUrl, true);
	}

	private static String getClientIp(HttpServletRequest request) {
		String x = request.getHeader("X-Forwarded-For");
		if (x != null && !x.isEmpty())
			return x.split(",")[0].trim();
		return request.getRemoteAddr();
	}
}
