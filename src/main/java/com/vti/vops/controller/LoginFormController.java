package com.vti.vops.controller;

import com.vti.vops.config.OidcProperties;
import com.vti.vops.entity.User;
import com.vti.vops.service.IAuditLogService;
import javax.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.ThreadLocalRandom;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpSession;

/**
 * 表单登录
 */
@Controller
@RequiredArgsConstructor
public class LoginFormController {

    private final IAuditLogService auditLogService;
    private final OidcProperties oidcProperties;

    @PostMapping("/login")
    public String doLogin(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam(required = false) String captchaAnswer,
            @RequestParam(defaultValue = "false") boolean rememberMe,
            Model model,
            HttpServletRequest request,
            HttpSession session
    ) {
        Object captchaObj = session.getAttribute("captcha");
        session.removeAttribute("captcha");
        if (captchaObj == null) {
            addCaptchaAndOidc(model, session);
            model.addAttribute("error", "验证码已失效，请刷新页面");
            return "login";
        }
        int expected = (Integer) captchaObj;
        try {
            int actual = captchaAnswer != null && !captchaAnswer.isBlank() ? Integer.parseInt(captchaAnswer.trim()) : -1;
            if (actual != expected) {
                addCaptchaAndOidc(model, session);
                model.addAttribute("error", "验证码错误");
                return "login";
            }
        } catch (NumberFormatException e) {
            addCaptchaAndOidc(model, session);
            model.addAttribute("error", "验证码格式错误");
            return "login";
        }
        Subject subject = SecurityUtils.getSubject();
        try {
            subject.login(new UsernamePasswordToken(username, password, rememberMe));
            User user = (User) subject.getPrincipal();
            auditLogService.log(user.getId(), user.getUsername(), "login", "auth", null, "form", getClientIp(request), request.getHeader("User-Agent"));
            return "redirect:/";
        } catch (AuthenticationException e) {
            addCaptchaAndOidc(model, session);
            model.addAttribute("error", "用户名或密码错误");
            return "login";
        }
    }

    private void addCaptchaAndOidc(Model model, HttpSession session) {
        int a = ThreadLocalRandom.current().nextInt(1, 21);
        int b = ThreadLocalRandom.current().nextInt(1, 21);
        session.setAttribute("captcha", a + b);
        model.addAttribute("captchaQuestion", a + " + " + b + " = ?");
        model.addAttribute("oidcEnabled", oidcProperties.isEnabled());
    }

    @GetMapping("/no-permission")
    public String noPermission() {
        return "no-permission";
    }

    @GetMapping("/logout")
    public String logout() {
        SecurityUtils.getSubject().logout();
        return "redirect:/login";
    }

    private static String getClientIp(HttpServletRequest request) {
        String x = request.getHeader("X-Forwarded-For");
        if (x != null && !x.isEmpty()) return x.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
