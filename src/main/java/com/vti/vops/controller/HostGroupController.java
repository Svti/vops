package com.vti.vops.controller;

import com.vti.vops.entity.HostGroup;
import com.vti.vops.entity.User;
import com.vti.vops.service.IAuditLogService;
import com.vti.vops.service.IHostService;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
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

/**
 * 主机分组管理：列表、新增/编辑、删除
 */
@Controller
@RequestMapping("/host/group")
@RequiredArgsConstructor
public class HostGroupController {

    private final IHostService hostService;
    private final IAuditLogService auditLogService;

    @GetMapping
    public String list(Model model) {
        List<HostGroup> groups = hostService.listGroups();
        Map<String, Long> groupIdToCount = new HashMap<>();
        for (HostGroup g : groups) {
            if (g.getId() != null) {
                groupIdToCount.put(String.valueOf(g.getId()), hostService.countByGroupId(g.getId()));
            }
        }
        model.addAttribute("groups", groups);
        model.addAttribute("groupIdToCount", groupIdToCount);
        return "host/group/list";
    }

    /** 弹窗用表单片段（新增不传 id，编辑传 id） */
    @GetMapping("/form")
    public String formFragment(@RequestParam(required = false) Long id, Model model) {
        if (id != null) {
            HostGroup g = hostService.listGroups().stream()
                    .filter(x -> id.equals(x.getId()))
                    .findFirst()
                    .orElse(null);
            if (g != null) {
                model.addAttribute("group", g);
                model.addAttribute("fragment", true);
                return "host/group/form";
            }
        }
        model.addAttribute("group", new HostGroup());
        model.addAttribute("fragment", true);
        return "host/group/form";
    }

    @PostMapping("/save")
    public String save(
            @RequestParam(required = false) Long id,
            @RequestParam String name,
            @RequestParam(required = false) String description,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        Subject subject = SecurityUtils.getSubject();
        User user = (User) subject.getPrincipal();
        name = name != null ? name.trim() : "";
        if (name.isEmpty()) {
            redirectAttributes.addFlashAttribute("message", "分组名称不能为空");
            return "redirect:/host/group";
        }
        HostGroup group = new HostGroup();
        group.setId(id);
        group.setName(name);
        group.setDescription(description != null ? description.trim() : null);
        try {
            hostService.saveGroup(group);
            auditLogService.log(user.getId(), user.getUsername(), "host_group.save", "host_group",
                    String.valueOf(group.getId()), group.getName(), getClientIp(request), request.getHeader("User-Agent"));
            redirectAttributes.addFlashAttribute("message", id == null ? "新增成功" : "保存成功");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("message", e.getMessage() != null ? e.getMessage() : "保存失败");
        }
        return "redirect:/host/group";
    }

    @PostMapping("/delete")
    public String delete(@RequestParam Long id, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        Subject subject = SecurityUtils.getSubject();
        User user = (User) subject.getPrincipal();
        try {
            hostService.removeGroupById(id);
            auditLogService.log(user.getId(), user.getUsername(), "host_group.delete", "host_group", String.valueOf(id), null, getClientIp(request), request.getHeader("User-Agent"));
            redirectAttributes.addFlashAttribute("message", "删除成功");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("message", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("message", e.getMessage() != null ? e.getMessage() : "删除失败");
        }
        return "redirect:/host/group";
    }

    private static String getClientIp(HttpServletRequest request) {
        String x = request.getHeader("X-Forwarded-For");
        if (x != null && !x.isEmpty()) return x.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
