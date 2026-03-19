package com.vti.vops.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vti.vops.entity.Host;
import com.vti.vops.entity.Permission;
import com.vti.vops.entity.Role;
import com.vti.vops.entity.RolePermission;
import com.vti.vops.entity.User;
import com.vti.vops.entity.UserRole;
import com.vti.vops.mapper.PermissionMapper;
import com.vti.vops.mapper.RoleMapper;
import com.vti.vops.mapper.RolePermissionMapper;
import com.vti.vops.mapper.UserRoleMapper;
import com.vti.vops.service.IHostService;
import com.vti.vops.service.IUserAuthService;
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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色管理：仅管理员可访问，列表与新增/编辑/删除。
 */
@Controller
@RequestMapping("/role")
@RequiredArgsConstructor
public class RoleController {

    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final IHostService hostService;
    private final IUserAuthService userAuthService;

    private boolean isAdmin() {
        Subject subject = SecurityUtils.getSubject();
        Object principal = subject.getPrincipal();
        if (!(principal instanceof User)) return false;
        return userAuthService.getRoleCodesByUserId(((User) principal).getId()).contains("admin");
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q, Model model) {
        if (!isAdmin()) {
            return "redirect:/";
        }
        LambdaQueryWrapper<Role> qw = new LambdaQueryWrapper<Role>().orderByAsc(Role::getId);
        if (q != null && !q.isBlank()) {
            String kw = q.trim();
            qw.and(w -> w
                    .like(Role::getRoleCode, kw)
                    .or().like(Role::getRoleName, kw)
                    .or().like(Role::getDescription, kw));
        }
        List<Role> roles = roleMapper.selectList(qw);
        Map<String, String> rolePermSummary = roles.stream().filter(r -> r.getId() != null).collect(Collectors.toMap(r -> String.valueOf(r.getId()), r -> {
            List<Long> pids = rolePermissionMapper.selectList(new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, r.getId()))
                    .stream().map(RolePermission::getPermissionId).distinct().toList();
            if (pids.isEmpty()) return "—";
            List<Permission> perms = permissionMapper.selectBatchIds(pids);
            boolean hasHostAll = perms.stream().anyMatch(p -> "host".equals(p.getResourceType()) && p.getResourceId() == null);
            if (hasHostAll) return "全部主机";
            long hostPermCount = perms.stream().filter(p -> "host".equals(p.getResourceType()) && p.getResourceId() != null).count();
            if (hostPermCount > 0) return "指定 " + hostPermCount + " 台主机";
            return "—";
        }));
        Map<String, Long> roleUserCount = roles.stream().filter(r -> r.getId() != null).collect(Collectors.toMap(r -> String.valueOf(r.getId()), r ->
                (long) userRoleMapper.selectCount(new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleId, r.getId()))));
        model.addAttribute("roles", roles);
        model.addAttribute("rolePermSummary", rolePermSummary);
        model.addAttribute("roleUserCount", roleUserCount);
        model.addAttribute("q", q != null ? q : "");
        return "role/list";
    }

    @GetMapping("/edit")
    public String edit(@RequestParam(required = false) Long id, Model model) {
        if (!isAdmin()) {
            return "redirect:/";
        }
        List<Permission> allPerms = permissionMapper.selectList(new LambdaQueryWrapper<Permission>().orderByAsc(Permission::getResourceType).orderByAsc(Permission::getPermCode));
        List<Permission> permissions = allPerms.stream().filter(p -> !"host".equals(p.getResourceType())).collect(Collectors.toList());
        Permission hostAllPerm = allPerms.stream().filter(p -> "host".equals(p.getResourceType()) && p.getResourceId() == null).findFirst().orElse(null);
        Long hostAllPermissionId = hostAllPerm != null ? hostAllPerm.getId() : null;
        model.addAttribute("permissions", permissions);
        model.addAttribute("hasResourcePermissions", !permissions.isEmpty());
        model.addAttribute("hostAllPermissionId", hostAllPermissionId);
        List<Host> hosts = hostService.list();
        model.addAttribute("hosts", hosts != null ? hosts : List.<Host>of());
        List<Long> rolePermissionIds = new ArrayList<>();
        Set<String> rolePermissionIdStrSet = Set.of();
        List<Long> roleHostIds = new ArrayList<>();
        Set<String> roleHostIdStrSet = Set.of();
        boolean roleHasHostAll = false;
        if (id != null) {
            Role role = roleMapper.selectById(id);
            if (role != null) {
                List<Long> pids = rolePermissionMapper.selectList(new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, id))
                        .stream().map(RolePermission::getPermissionId).filter(pid -> pid != null).distinct().toList();
                roleHasHostAll = hostAllPermissionId != null && pids.contains(hostAllPermissionId);
                rolePermissionIds = permissionMapper.selectBatchIds(pids).stream()
                        .filter(p -> !"host".equals(p.getResourceType()))
                        .map(Permission::getId).filter(pid -> pid != null).collect(Collectors.toList());
                rolePermissionIdStrSet = rolePermissionIds.stream().map(Objects::toString).collect(Collectors.toSet());
                roleHostIds = permissionMapper.selectBatchIds(pids).stream()
                        .filter(p -> "host".equals(p.getResourceType()) && p.getResourceId() != null)
                        .map(Permission::getResourceId).filter(Objects::nonNull).distinct().toList();
                roleHostIdStrSet = roleHostIds.stream().map(Objects::toString).collect(Collectors.toSet());
                model.addAttribute("role", role);
                model.addAttribute("rolePermissionIds", rolePermissionIds);
                model.addAttribute("rolePermissionIdStrSet", rolePermissionIdStrSet);
                model.addAttribute("roleHostIds", roleHostIds);
                model.addAttribute("roleHostIdStrSet", roleHostIdStrSet);
                model.addAttribute("roleHasHostAll", roleHasHostAll);
                return "role/edit";
            }
        }
        model.addAttribute("role", new Role());
        model.addAttribute("rolePermissionIds", rolePermissionIds);
        model.addAttribute("rolePermissionIdStrSet", rolePermissionIdStrSet);
        model.addAttribute("roleHostIds", roleHostIds);
        model.addAttribute("roleHostIdStrSet", roleHostIdStrSet);
        model.addAttribute("roleHasHostAll", roleHasHostAll);
        return "role/edit";
    }

    @PostMapping("/save")
    public String save(
            @RequestParam(required = false) Long id,
            @RequestParam String roleCode,
            @RequestParam String roleName,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) List<Long> permissionIds,
            @RequestParam(required = false) String hostPermissionMode,
            @RequestParam(required = false) List<Long> hostIds,
            RedirectAttributes redirectAttributes) {
        if (!isAdmin()) {
            return "redirect:/";
        }
        roleCode = roleCode != null ? roleCode.trim() : "";
        roleName = roleName != null ? roleName.trim() : "";
        if (roleCode.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "角色编码不能为空");
            return "redirect:/role/edit" + (id != null ? "?id=" + id : "");
        }
        Role role = id != null ? roleMapper.selectById(id) : new Role();
        if (role == null) role = new Role();
        role.setRoleCode(roleCode);
        role.setRoleName(roleName.isEmpty() ? roleCode : roleName);
        role.setDescription(description != null && !description.isBlank() ? description.trim() : null);
        if (role.getId() != null) {
            roleMapper.updateById(role);
            redirectAttributes.addFlashAttribute("message", "角色已更新");
        } else {
            long count = roleMapper.selectCount(new LambdaQueryWrapper<Role>().eq(Role::getRoleCode, roleCode));
            if (count > 0) {
                redirectAttributes.addFlashAttribute("error", "角色编码已存在");
                return "redirect:/role/edit";
            }
            roleMapper.insert(role);
            redirectAttributes.addFlashAttribute("message", "角色已添加");
        }
        Long roleId = role.getId();
        if (roleId != null) {
            rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, roleId));
            List<Long> finalPermIds = permissionIds != null ? new ArrayList<>(permissionIds) : new ArrayList<>();
            finalPermIds.removeIf(Objects::isNull);
            Permission hostAllPerm = permissionMapper.selectList(
                    new LambdaQueryWrapper<Permission>().eq(Permission::getResourceType, "host").isNull(Permission::getResourceId)).stream().findFirst().orElse(null);
            if ("all".equals(hostPermissionMode) && hostAllPerm != null) {
                if (!finalPermIds.contains(hostAllPerm.getId())) finalPermIds.add(hostAllPerm.getId());
            }
            if ("specific".equals(hostPermissionMode) && hostIds != null && !hostIds.isEmpty()) {
                for (Long hostId : hostIds) {
                    if (hostId == null) continue;
                    Long permId = findOrCreateHostPermission(hostId);
                    if (permId != null && !finalPermIds.contains(permId)) finalPermIds.add(permId);
                }
            }
            for (Long permId : finalPermIds) {
                RolePermission rp = new RolePermission();
                rp.setRoleId(roleId);
                rp.setPermissionId(permId);
                rolePermissionMapper.insert(rp);
            }
        }
        return "redirect:/role";
    }

    @PostMapping("/delete")
    public String delete(@RequestParam Long id, RedirectAttributes redirectAttributes) {
        if (!isAdmin()) {
            return "redirect:/";
        }
        if (id != null) {
            long used = userRoleMapper.selectCount(new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleId, id));
            if (used > 0) {
                redirectAttributes.addFlashAttribute("error", "该角色下还有用户，无法删除");
                return "redirect:/role";
            }
            rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, id));
            roleMapper.deleteById(id);
            redirectAttributes.addFlashAttribute("message", "角色已删除");
        }
        return "redirect:/role";
    }

    /** 按主机 ID 查找或创建一条 host 资源权限，返回 permission_id */
    private Long findOrCreateHostPermission(Long hostId) {
        Permission p = permissionMapper.selectOne(new LambdaQueryWrapper<Permission>()
                .eq(Permission::getResourceType, "host")
                .eq(Permission::getResourceId, hostId)
                .last("LIMIT 1"));
        if (p != null && p.getId() != null) return p.getId();
        Permission newPerm = new Permission();
        newPerm.setPermCode("host:" + hostId);
        newPerm.setPermName("主机 " + hostId);
        newPerm.setResourceType("host");
        newPerm.setResourceId(hostId);
        newPerm.setCreateTime(new Date());
        newPerm.setUpdateTime(new Date());
        newPerm.setDeleted(0);
        permissionMapper.insert(newPerm);
        return newPerm.getId();
    }
}
