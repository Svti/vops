package com.vti.vops.security;

import com.vti.vops.entity.User;
import com.vti.vops.service.IUserAuthService;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.web.filter.AccessControlFilter;
import org.apache.shiro.web.util.WebUtils;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.util.List;
import java.util.function.Predicate;

/**
 * 要求已登录且至少拥有一个角色；无角色用户重定向到 /no-permission。
 * 匿名路径由 ShiroConfig 注入，与链上 anon 共用同一配置；若本 filter 因 /** 先被匹配到则按该配置放行。
 */
public class RequireRoleFilter extends AccessControlFilter {

    private IUserAuthService userAuthService;
    private Predicate<String> anonPathPredicate;

    public void setUserAuthService(IUserAuthService userAuthService) {
        this.userAuthService = userAuthService;
    }

    public void setAnonPathPredicate(Predicate<String> anonPathPredicate) {
        this.anonPathPredicate = anonPathPredicate;
    }

    @Override
    protected boolean isAccessAllowed(ServletRequest request, ServletResponse response, Object mappedValue) {
        String path = WebUtils.getPathWithinApplication(WebUtils.toHttp(request));
        if (anonPathPredicate != null && anonPathPredicate.test(path)) {
            return true;
        }
        if (isLoginRequest(request, response)) {
            return true;
        }
        Subject subject = getSubject(request, response);
        if (!subject.isAuthenticated()) {
            return false;
        }
        Object principal = subject.getPrincipal();
        Long userId = null;
        if (principal instanceof User) {
            userId = ((User) principal).getId();
        }
        if (userId == null) {
            return false;
        }
        List<String> roles = userAuthService.getRoleCodesByUserId(userId);
        return roles != null && !roles.isEmpty();
    }

    @Override
    protected boolean onAccessDenied(ServletRequest request, ServletResponse response) throws Exception {
        Subject subject = getSubject(request, response);
        if (!subject.isAuthenticated()) {
            saveRequestAndRedirectToLogin(request, response);
            return false;
        }
        WebUtils.issueRedirect(request, response, "/no-permission");
        return false;
    }
}
