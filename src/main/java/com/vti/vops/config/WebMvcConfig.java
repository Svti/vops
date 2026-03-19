package com.vti.vops.config;

import com.vti.vops.entity.User;
import com.vti.vops.service.IUserAuthService;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import javax.servlet.http.HttpServletRequest;

/**
 * 全局 Model：为所有视图注入当前用户、是否管理员、当前导航标识，便于 layout 导航栏显示与高亮。
 */
@ControllerAdvice
@RequiredArgsConstructor
public class WebMvcConfig {

    private final IUserAuthService userAuthService;

    @ModelAttribute
    public void addUser(Model model) {
        Subject subject = SecurityUtils.getSubject();
        if (subject != null && subject.getPrincipal() != null) {
            Object principal = subject.getPrincipal();
            model.addAttribute("user", principal);
            boolean admin = principal instanceof User
                    && userAuthService.getRoleCodesByUserId(((User) principal).getId()).contains("admin");
            model.addAttribute("isAdmin", admin);
        } else {
            model.addAttribute("isAdmin", false);
        }
    }

    @ModelAttribute
    public void addNav(Model model, HttpServletRequest request) {
        String path = request.getRequestURI();
        String nav = "index";
        if (path != null) {
            if (path.startsWith("/host")) nav = "host";
            else if (path.startsWith("/monitor")) nav = "monitor";
            else if (path.startsWith("/batch")) nav = "batch";
            else if (path.startsWith("/alert")) nav = "alert";
            else if (path.startsWith("/sshkey")) nav = "sshkey";
            else if (path.startsWith("/notifier")) nav = "notifier";
            else if (path.startsWith("/user")) nav = "user";
            else if (path.startsWith("/role")) nav = "role";
            else if (path.startsWith("/audit")) nav = "audit";
            else if (path.startsWith("/settings")) nav = "settings";
        }
        model.addAttribute("nav", nav);
    }
}
