package com.vti.vops.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vti.vops.entity.Role;
import com.vti.vops.entity.User;
import com.vti.vops.entity.UserRole;
import com.vti.vops.mapper.RoleMapper;
import com.vti.vops.mapper.UserRoleMapper;
import com.vti.vops.service.IUserAuthService;
import com.vti.vops.service.IUserService;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户：个人修改密码；管理员可进行用户列表、新增/编辑、重置密码、删除
 */
@Controller
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final IUserService userService;
    private final IUserAuthService userAuthService;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;

    private boolean isAdmin() {
        Subject subject = SecurityUtils.getSubject();
        Object principal = subject.getPrincipal();
        if (!(principal instanceof User)) return false;
        return userAuthService.getRoleCodesByUserId(((User) principal).getId()).contains("admin");
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q, Model model) {
        if (!isAdmin()) {
            return "redirect:/user/password";
        }
        List<User> users = userService.list();
        if (q != null && !q.isBlank()) {
            String kw = q.trim().toLowerCase();
            users = users.stream()
                    .filter(u -> (u.getUsername() != null && u.getUsername().toLowerCase().contains(kw))
                            || (u.getRealName() != null && u.getRealName().toLowerCase().contains(kw))
                            || (u.getEmail() != null && u.getEmail().toLowerCase().contains(kw)))
                    .collect(Collectors.toList());
        }
        Map<String, List<Long>> userRoleIds = new HashMap<>();
        for (User u : users) {
            if (u.getId() != null) {
                userRoleIds.put(String.valueOf(u.getId()), userService.getRoleIdsByUserId(u.getId()));
            }
        }
        model.addAttribute("users", users);
        model.addAttribute("userRoleIds", userRoleIds);
        model.addAttribute("roles", roleMapper.selectList(null));
        model.addAttribute("q", q != null ? q : "");
        return "user/list";
    }

    /** 弹窗用表单片段（新增不传 id，编辑传 id） */
    @GetMapping("/form")
    public String formFragment(@RequestParam(required = false) Long id, Model model) {
        if (!isAdmin()) {
            return "redirect:/user/password";
        }
        if (id != null) {
            User user = userService.getById(id);
            if (user != null) {
                user.setPassword(null);
                user.setSalt(null);
                model.addAttribute("user", user);
                model.addAttribute("userRoleIds", userService.getRoleIdsByUserId(id));
                model.addAttribute("roles", roleMapper.selectList(null));
                model.addAttribute("fragment", true);
                return "user/edit";
            }
        }
        model.addAttribute("user", new User());
        model.addAttribute("userRoleIds", List.<Long>of());
        model.addAttribute("roles", roleMapper.selectList(null));
        model.addAttribute("fragment", true);
        return "user/edit";
    }

    @GetMapping("/edit")
    public String edit(@RequestParam(required = false) Long id, Model model) {
        if (!isAdmin()) {
            return "redirect:/user/password";
        }
        if (id != null) {
            User user = userService.getById(id);
            if (user != null) {
                user.setPassword(null);
                user.setSalt(null);
                model.addAttribute("user", user);
                model.addAttribute("userRoleIds", userService.getRoleIdsByUserId(id));
                model.addAttribute("roles", roleMapper.selectList(null));
                return "user/edit";
            }
        }
        model.addAttribute("user", new User());
        model.addAttribute("userRoleIds", List.<Long>of());
        model.addAttribute("roles", roleMapper.selectList(null));
        return "user/edit";
    }

    @PostMapping("/save")
    public Object save(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String password,
            @RequestParam(required = false) String realName,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) List<Long> roleIds,
            RedirectAttributes ra,
            HttpServletRequest request) {
        if (!isAdmin()) {
            if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
                return ResponseEntity.status(403).body(Map.of("success", false, "message", "无权限"));
            }
            return "redirect:/user/password";
        }
        User user = id != null ? userService.getById(id) : new User();
        if (user == null) user = new User();
        if (username != null && !username.isBlank()) user.setUsername(username.trim());
        if (realName != null) user.setRealName(realName.trim().isEmpty() ? null : realName.trim());
        if (email != null) user.setEmail(email.trim().isEmpty() ? null : email.trim());
        user.setStatus(status != null && status == 1 ? 1 : 0);
        try {
            userService.saveUserWithRoles(user, password, roleIds != null ? roleIds : List.of());
        } catch (IllegalArgumentException e) {
            if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
            }
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/user/edit" + (id != null ? "?id=" + id : "");
        }
        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            return ResponseEntity.ok(Map.of("success", true));
        }
        ra.addFlashAttribute("message", "保存成功");
        return "redirect:/user";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam Long id, @RequestParam String newPassword, @RequestParam String confirmPassword, RedirectAttributes ra) {
        if (!isAdmin()) {
            return "redirect:/user/password";
        }
        if (!newPassword.equals(confirmPassword)) {
            ra.addFlashAttribute("error", "两次输入的密码不一致");
            return "redirect:/user";
        }
        try {
            userService.resetPasswordByAdmin(id, newPassword);
            ra.addFlashAttribute("message", "密码已重置");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/user";
    }

    @PostMapping("/delete")
    public String delete(@RequestParam(required = false) Long id, RedirectAttributes ra) {
        if (!isAdmin()) {
            return "redirect:/user/password";
        }
        if (id == null) {
            ra.addFlashAttribute("error", "用户不存在");
            return "redirect:/user";
        }
        Subject subject = SecurityUtils.getSubject();
        Object principal = subject.getPrincipal();
        if (principal instanceof User && id.equals(((User) principal).getId())) {
            ra.addFlashAttribute("error", "不能删除当前登录用户");
            return "redirect:/user";
        }
        if (userAuthService.getRoleCodesByUserId(id).contains("admin")) {
            List<Long> adminRoleIds = roleMapper.selectList(new LambdaQueryWrapper<Role>().eq(Role::getRoleCode, "admin"))
                    .stream().map(Role::getId).filter(rid -> rid != null).toList();
            long adminUserCount = adminRoleIds.isEmpty() ? 0 : userRoleMapper.selectList(
                    new LambdaQueryWrapper<UserRole>().in(UserRole::getRoleId, adminRoleIds))
                    .stream().map(UserRole::getUserId).distinct().count();
            if (adminUserCount <= 1) {
                ra.addFlashAttribute("error", "不能删除最后一名管理员");
                return "redirect:/user";
            }
        }
        userService.removeById(id);
        ra.addFlashAttribute("message", "已删除");
        return "redirect:/user";
    }

    @GetMapping("/password")
    public String passwordPage(Model model) {
        Subject subject = SecurityUtils.getSubject();
        Object principal = subject.getPrincipal();
        if (!(principal instanceof User)) {
            return "redirect:/login";
        }
        model.addAttribute("nav", "user");
        return "user/password";
    }

    @PostMapping("/password")
    public String changePassword(
            @RequestParam String oldPassword,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            RedirectAttributes redirectAttributes
    ) {
        Subject subject = SecurityUtils.getSubject();
        Object principal = subject.getPrincipal();
        if (!(principal instanceof User)) {
            return "redirect:/login";
        }
        User user = (User) principal;
        if (newPassword == null || newPassword.length() < 6) {
            redirectAttributes.addFlashAttribute("error", "新密码至少 6 位");
            return "redirect:/user/password";
        }
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "两次输入的新密码不一致");
            return "redirect:/user/password";
        }
        try {
            userService.updatePassword(user.getId(), oldPassword, newPassword);
            redirectAttributes.addFlashAttribute("success", "密码已修改，请重新登录");
            subject.logout();
            return "redirect:/login";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/user/password";
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/user/password";
        }
    }
}
