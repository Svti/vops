package com.vti.vops.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vti.vops.dto.HostListDataVo;
import com.vti.vops.dto.HostListMetaVo;
import com.vti.vops.dto.HostStatusVo;
import com.vti.vops.entity.Host;
import com.vti.vops.entity.HostGroup;
import com.vti.vops.entity.User;
import com.vti.vops.monitor.IcmpCollector;
import com.vti.vops.monitor.MonitorCollector;
import com.vti.vops.service.IAlertRuleService;
import com.vti.vops.service.IAuditLogService;
import com.vti.vops.service.IHostAlertRuleService;
import com.vti.vops.service.IHostListCacheService;
import com.vti.vops.service.IHostService;
import com.vti.vops.service.ISshKeyService;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 主机管理（主机级权限）
 */
@Controller
@RequestMapping("/host")
@RequiredArgsConstructor
public class HostController {

    private final IHostService hostService;
    private final IHostListCacheService hostListCacheService;
    private final IAuditLogService auditLogService;
    private final ISshKeyService sshKeyService;
    private final IAlertRuleService alertRuleService;
    private final IHostAlertRuleService hostAlertRuleService;
    private final MonitorCollector monitorCollector;
    private final IcmpCollector icmpCollector;

    private static final int DEFAULT_PAGE_SIZE = 20;

    @GetMapping
    public String list(@RequestParam(defaultValue = "1") int page,
                      @RequestParam(defaultValue = "20") int size,
                      @RequestParam(required = false) String q,
                      @RequestParam(required = false) Long groupId,
                      @RequestParam(required = false) Integer statusFilter,
                      @RequestParam(required = false) Integer onlineFilter,
                      Model model) {
        if (page < 1) page = 1;
        if (size < 1 || size > 100) size = DEFAULT_PAGE_SIZE;
        User user = (User) SecurityUtils.getSubject().getPrincipal();
        HostListDataVo data = hostListCacheService.getHostListData(user.getId());
        HostListMetaVo meta = hostListCacheService.getHostListMeta();
        List<Host> allHosts = data.getHosts() != null ? data.getHosts() : List.of();
        if (q != null && !q.isBlank()) {
            String kw = q.trim().toLowerCase();
            allHosts = allHosts.stream()
                    .filter(h -> (h.getName() != null && h.getName().toLowerCase().contains(kw))
                            || (h.getHostname() != null && h.getHostname().toLowerCase().contains(kw)))
                    .collect(Collectors.toList());
        }
        if (groupId != null) {
            Long gid = groupId;
            allHosts = allHosts.stream()
                    .filter(h -> h.getGroupId() != null && h.getGroupId().equals(gid))
                    .collect(Collectors.toList());
        }
        if (statusFilter != null) {
            Integer s = statusFilter;
            allHosts = allHosts.stream()
                    .filter(h -> h.getStatus() != null && h.getStatus().equals(s))
                    .collect(Collectors.toList());
        }
        if (onlineFilter != null) {
            boolean wantOnline = (onlineFilter == 1);
            allHosts = allHosts.stream()
                    .filter(h -> {
                        HostStatusVo st = data.getStatusMap() != null ? data.getStatusMap().get(h.getId()) : null;
                        boolean isOnline = st != null && st.isOnline();
                        return wantOnline == isOnline;
                    })
                    .collect(Collectors.toList());
        }
        int total = allHosts.size();
        int totalPages = total == 0 ? 1 : (total + size - 1) / size;
        int from = (page - 1) * size;
        List<Host> hosts = from >= total ? List.of() : allHosts.subList(from, Math.min(from + size, total));
        List<Long> hostIds = hosts.stream().map(Host::getId).filter(Objects::nonNull).collect(Collectors.toList());
        Map<String, HostStatusVo> statusMap = new LinkedHashMap<>();
        Map<String, Integer> hostIdToRuleCount = new HashMap<>();
        if (data.getStatusMap() != null && data.getHostIdToRuleCount() != null) {
            for (Long hid : hostIds) {
                String key = String.valueOf(hid);
                if (data.getStatusMap().containsKey(hid)) {
                    statusMap.put(key, data.getStatusMap().get(hid));
                } else {
                    statusMap.put(key, HostStatusVo.builder().online(false).build());
                }
                hostIdToRuleCount.put(key, data.getHostIdToRuleCount().getOrDefault(hid, 0));
            }
        }
        Map<String, String> groupIdToName = new HashMap<>();
        if (meta.getGroups() != null) {
            for (HostGroup g : meta.getGroups()) {
                if (g.getId() != null) groupIdToName.put(String.valueOf(g.getId()), g.getName() != null ? g.getName() : "");
            }
        }
        model.addAttribute("hosts", hosts);
        model.addAttribute("hostStatusMap", statusMap);
        model.addAttribute("hostIdToRuleCount", hostIdToRuleCount);
        model.addAttribute("groupIdToName", groupIdToName);
        model.addAttribute("groups", meta.getGroups() != null ? meta.getGroups() : List.of());
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("total", total);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("q", q != null ? q.trim() : "");
        model.addAttribute("groupId", groupId);
        model.addAttribute("statusFilter", statusFilter);
        model.addAttribute("onlineFilter", onlineFilter);
        model.addAttribute("alertRules", meta.getAlertRules() != null ? meta.getAlertRules() : List.of());
        model.addAttribute("sshKeys", meta.getSshKeys() != null ? meta.getSshKeys() : List.of());
        return "host/list";
    }

    /** 弹窗用表单片段（新增不传 id，编辑传 id，复制传 copyId） */
    @GetMapping("/form")
    public String formFragment(@RequestParam(required = false) Long id,
                              @RequestParam(required = false) Long copyId,
                              Model model) {
        if (copyId != null) {
            Optional.ofNullable(hostService.getById(copyId)).ifPresent(h -> {
                h.setId(null);
                h.setCredential(null);
                String name = h.getName() != null ? h.getName().trim() : "";
                h.setName(name.isEmpty() ? "副本" : name + " (副本)");
                model.addAttribute("host", h);
            });
        }
        if (id != null && !model.containsAttribute("host")) {
            Optional.ofNullable(hostService.getById(id)).ifPresent(h -> {
                h.setCredential(null);
                model.addAttribute("host", h);
            });
        }
        if (!model.containsAttribute("host")) {
            Host host = new Host();
            host.setPort(22);
            host.setAuthType("password");
            model.addAttribute("host", host);
        }
        model.addAttribute("groups", hostService.listGroups());
        model.addAttribute("sshKeys", sshKeyService.listNames());
        model.addAttribute("fragment", true);
        return "host/edit";
    }

    /** 监控规则弹窗：获取某主机的规则勾选表单片段 */
    @GetMapping("/rules-form")
    public String rulesFormFragment(@RequestParam Long hostId, Model model) {
        Subject subject = SecurityUtils.getSubject();
        User user = (User) subject.getPrincipal();
        List<Host> allowed = hostService.listForUserIncludeDisabled(user.getId());
        if (!allowed.stream().anyMatch(h -> hostId.equals(h.getId()))) {
            return "host/rules-form-denied";
        }
        Host host = hostService.getById(hostId);
        model.addAttribute("host", host);
        model.addAttribute("hostId", hostId);
        model.addAttribute("hostName", host != null ? (host.getName() != null ? host.getName() : host.getHostname()) : "");
        model.addAttribute("alertRules", alertRuleService.list());
        model.addAttribute("hostAlertRuleIds", hostAlertRuleService.listRuleIdsByHostId(hostId));
        return "host/rules-form";
    }

    @PostMapping("/save-rules")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveRules(@RequestParam Long hostId, @RequestParam(required = false) List<Long> ruleIds, HttpServletRequest request) {
        Subject subject = SecurityUtils.getSubject();
        User user = (User) subject.getPrincipal();
        List<Host> allowed = hostService.listForUserIncludeDisabled(user.getId());
        if (!allowed.stream().anyMatch(h -> hostId.equals(h.getId()))) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "无权限"));
        }
        hostAlertRuleService.setRulesForHost(hostId, ruleIds != null ? ruleIds : List.of());
        Host h = hostService.getById(hostId);
        auditLogService.log(user.getId(), user.getUsername(), "host.rules", "host", String.valueOf(hostId), h != null ? h.getName() : null, getClientIp(request), request.getHeader("User-Agent"));
        return ResponseEntity.ok(Map.of("success", true));
    }

    /** 立即采集该主机指标，仅允许有权限的主机。ajax=1 时返回 JSON，否则重定向到列表 */
    @GetMapping("/refresh")
    public ResponseEntity<?> refreshMetrics(@RequestParam Long hostId,
                                           @RequestParam(required = false) Boolean ajax,
                                           HttpServletRequest request) {
        Subject subject = SecurityUtils.getSubject();
        User user = (User) subject.getPrincipal();
        List<Host> allowed = hostService.listForUser(user.getId());
        Optional<Host> hostOpt = allowed.stream().filter(h -> hostId.equals(h.getId())).findFirst();
        if (hostOpt.isEmpty()) {
            if (Boolean.TRUE.equals(ajax)) {
                return ResponseEntity.status(403).body(Map.of("success", false, "message", "无权限"));
            }
            return ResponseEntity.status(302).header("Location", "/host").build();
        }
        monitorCollector.collectOne(hostId);
        Host host = hostOpt.get();
        if (host.getHostname() != null && !host.getHostname().isBlank()) {
            icmpCollector.collectOne(hostId, host.getHostname().trim());
        }
        auditLogService.log(user.getId(), user.getUsername(), "host.refresh", "host", String.valueOf(hostId), host.getName(), getClientIp(request), request.getHeader("User-Agent"));
        if (Boolean.TRUE.equals(ajax)) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(Map.of("success", true));
        }
        return ResponseEntity.status(302).header("Location", "/host").build();
    }

    @GetMapping("/edit")
    public String edit(@RequestParam(required = false) Long id, Model model) {
        if (id != null) {
            Optional.ofNullable(hostService.getById(id)).ifPresent(h -> {
                h.setCredential(null);
                model.addAttribute("host", h);
            });
        }
        if (!model.containsAttribute("host")) {
            Host host = new Host();
            host.setPort(22);
            host.setAuthType("password");
            model.addAttribute("host", host);
        }
        model.addAttribute("groups", hostService.listGroups());
        model.addAttribute("sshKeys", sshKeyService.listNames());
        return "host/edit";
    }

    @PostMapping("/save")
    public Object save(Host host,
                      @RequestParam(required = false) Long sshKeyId,
                      @RequestParam(required = false) String password,
                      HttpServletRequest request) {
        if (sshKeyId != null && "privateKey".equals(host.getAuthType())) {
            host.setSshKeyId(sshKeyId);
            host.setCredential(null);
        } else {
            host.setSshKeyId(null);
            // 密码认证：仅用显式 password 参数写入 credential，避免与私钥的 credential 绑定混淆
            if (password != null && !password.isBlank()) {
                host.setCredential(password);
            }
        }
        Subject subject = SecurityUtils.getSubject();
        User user = (User) subject.getPrincipal();
        hostService.save(host);
        auditLogService.log(user.getId(), user.getUsername(), "host.save", "host", String.valueOf(host.getId()), host.getName(), getClientIp(request), request.getHeader("User-Agent"));
        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            return ResponseEntity.ok(Map.of("success", true));
        }
        return "redirect:/host";
    }

    /**
     * 导入主机：仅支持 JumpServer 资产导出的 CSV。表头需含：主机名、IP、协议组、激活、备注（可选）。
     * 协议组格式如 ['ssh/22']，端口从协议组解析，缺省 22；激活为 FALSE 的行跳过。
     */
    @PostMapping("/import")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> importHosts(
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(required = false) String content,
            HttpServletRequest request) {
        Subject subject = SecurityUtils.getSubject();
        User user = (User) subject.getPrincipal();
        String csvText = null;
        if (file != null && !file.isEmpty()) {
            try {
                csvText = new String(file.getBytes(), StandardCharsets.UTF_8);
                if (csvText.startsWith("\uFEFF")) csvText = csvText.substring(1);
            } catch (Exception e) {
                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "message", "文件读取失败",
                        "imported", 0,
                        "failed", 0,
                        "errors", List.of(Map.of("row", 0, "message", e.getMessage()))));
            }
        } else if (content != null && !content.isBlank()) {
            csvText = content.trim();
            if (csvText.startsWith("\uFEFF")) csvText = csvText.substring(1);
        }
        if (csvText == null || csvText.isBlank()) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "请上传 JumpServer 资产导出的 CSV 文件或粘贴内容",
                    "imported", 0,
                    "failed", 0,
                    "errors", Collections.<Map<String, Object>>emptyList()));
        }
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new java.io.ByteArrayInputStream(csvText.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) lines.add(line);
            }
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "内容解析失败",
                    "imported", 0,
                    "failed", 0,
                    "errors", List.of(Map.of("row", 0, "message", e.getMessage()))));
        }
        if (lines.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "CSV 为空",
                    "imported", 0,
                    "failed", 0,
                    "errors", Collections.<Map<String, Object>>emptyList()));
        }
        String[] headerParts = parseCsvLine(lines.get(0));
        int idxName = indexOfColumnAny(headerParts, "*名称", "名称", "主机名", "Hostname", "hostname");
        int idxIp = indexOfColumnAny(headerParts, "*IP/主机", "IP/主机", "IP", "ip", "Ip", "地址");
        int idxProtocol = indexOfColumnAny(headerParts, "协议组", "协议");
        int idxActive = indexOfColumnAny(headerParts, "激活中", "激活");
        int idxRemark = indexOfColumnAny(headerParts, "备注");
        int idxNode = indexOfColumnAny(headerParts, "节点路径", "节点");
        int idxGroup = indexOfColumnAny(headerParts, "分组", "节点路径", "节点");
        if (idxName < 0 || idxIp < 0) {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "CSV 表头需包含「*名称」与「*IP/主机」列（JumpServer 资产导出格式）。当前表头："
                            + String.join(",", headerParts),
                    "imported", 0,
                    "failed", 0,
                    "errors", Collections.<Map<String, Object>>emptyList()));
        }
        int imported = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            String[] parts = parseCsvLine(line);
            if (idxIp >= parts.length) {
                errors.add(Map.of("row", i + 1, "message", "列数不足"));
                continue;
            }
            String ip = getCell(parts, idxIp);
            String nameCol = getCell(parts, idxName);
            if (ip.isEmpty()) {
                errors.add(Map.of("row", i + 1, "message", "IP 不能为空"));
                continue;
            }
            if (idxActive >= 0 && idxActive < parts.length) {
                String active = getCell(parts, idxActive);
                if ("false".equalsIgnoreCase(active) || "FALSE".equals(active) || "0".equals(active)) continue;
            }
            int port = parseSshPortFromProtocolGroup(idxProtocol >= 0 && idxProtocol < parts.length ? getCell(parts, idxProtocol) : "");
            String name = nameCol != null && !nameCol.isEmpty() ? nameCol : ip;
            String remark = idxRemark >= 0 && idxRemark < parts.length ? getCell(parts, idxRemark) : "";
            String nodePath = idxNode >= 0 && idxNode < parts.length ? getCell(parts, idxNode) : "";
            String groupName = idxGroup >= 0 && idxGroup < parts.length ? getCell(parts, idxGroup) : "";
            if (groupName == null) groupName = "";
            groupName = groupName.trim();
            if (groupName.isEmpty() && (nodePath != null && !nodePath.isEmpty())) groupName = nodePath.trim();
            groupName = parseGroupNameFromNode(groupName);
            String description = (remark != null && !remark.isEmpty()) ? remark : "";
            Host existing = hostService.getOne(new LambdaQueryWrapper<Host>()
                    .eq(Host::getHostname, ip).eq(Host::getPort, port).last("LIMIT 1"));
            Host host = new Host();
            host.setName(name);
            host.setHostname(ip);
            host.setPort(port);
            host.setDescription(description);
            if (!groupName.isEmpty()) {
                Long groupId = hostService.getOrCreateGroupByName(groupName);
                if (groupId != null) host.setGroupId(groupId);
            } else {
                host.setGroupId(null);
            }
            if (existing == null) {
                host.setUsername("root");
                host.setCredential(null);
                host.setAuthType("password");
                host.setStatus(1);
            }
            try {
                if (existing != null) {
                    // 已存在时只更新名称、IP、端口、备注、分组，不修改用户名与认证方式
                    hostService.update(new LambdaUpdateWrapper<Host>()
                            .eq(Host::getId, existing.getId())
                            .set(Host::getName, name)
                            .set(Host::getHostname, ip)
                            .set(Host::getPort, port)
                            .set(Host::getDescription, description)
                            .set(Host::getGroupId, host.getGroupId()));
                    host.setId(existing.getId());
                } else {
                    hostService.save(host);
                }
                imported++;
                auditLogService.log(user.getId(), user.getUsername(), "host.import", "host", String.valueOf(host.getId()), host.getName(), getClientIp(request), request.getHeader("User-Agent"));
            } catch (Exception e) {
                errors.add(Map.of("row", i + 1, "message", e.getMessage() != null ? e.getMessage() : "保存失败"));
            }
        }
        String successMessage = null;
        if (imported == 0 && errors.isEmpty() && lines.size() > 1) {
            successMessage = "未导入任何主机（可能所有行均为未激活，或数据行格式有误）";
        } else if (imported == 0 && lines.size() <= 1) {
            successMessage = "CSV 仅有表头，无数据行";
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("imported", imported);
        body.put("failed", errors.size());
        body.put("errors", errors);
        if (successMessage != null) body.put("message", successMessage);
        return ResponseEntity.ok(body);
    }

    private static int indexOfColumnAny(String[] headerParts, String... colNames) {
        if (colNames == null) return -1;
        for (String colName : colNames) {
            int i = indexOfColumn(headerParts, colName);
            if (i >= 0) return i;
        }
        return -1;
    }

    private static int indexOfColumn(String[] headerParts, String colName) {
        if (colName == null) return -1;
        for (int i = 0; i < headerParts.length; i++) {
            if (colName.equals(headerParts[i].trim())) return i;
        }
        return -1;
    }

    private static String getCell(String[] parts, int index) {
        if (index < 0 || index >= parts.length) return "";
        String s = parts[index];
        return s != null ? s.trim() : "";
    }

    /**
     * JumpServer 导出的分组/节点为 JSON 数组如 ["/DEFAULT/内网/发布系统"]，解析取第一个路径；
     * 若解析失败则按纯文本去掉首尾中括号（如 [Default]）。
     */
    private static String parseGroupNameFromNode(String s) {
        if (s == null || (s = s.trim()).isEmpty()) return s;
        if (s.startsWith("[")) {
            try {
                String[] arr = new ObjectMapper().readValue(s, String[].class);
                if (arr != null && arr.length > 0 && arr[0] != null) {
                    String path = arr[0].trim();
                    if (!path.isEmpty()) {
                        while (path.startsWith("/")) path = path.substring(1);
                        return path;
                    }
                }
            } catch (Exception ignored) { }
            return stripBrackets(s);
        }
        return s;
    }

    private static String stripBrackets(String s) {
        if (s == null || s.isEmpty()) return s;
        s = s.trim();
        if (s.length() >= 2 && s.startsWith("[") && s.endsWith("]")) {
            return s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

    /** 从 JumpServer 协议组如 ['ssh/22'] 或 ['ssh/22','rdp/3389'] 解析 SSH 端口，缺省 22 */
    private static int parseSshPortFromProtocolGroup(String protocolGroup) {
        if (protocolGroup == null || protocolGroup.isEmpty()) return 22;
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("ssh/(\\d+)");
        java.util.regex.Matcher m = p.matcher(protocolGroup);
        if (m.find()) {
            try {
                int port = Integer.parseInt(m.group(1));
                if (port >= 1 && port <= 65535) return port;
            } catch (NumberFormatException ignored) {}
        }
        return 22;
    }

    private static String[] parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuote = false;
        StringBuilder cell = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuote = !inQuote;
            } else if (inQuote) {
                cell.append(c);
            } else if (c == ',' || c == '\t') {
                result.add(cell.toString().trim());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        result.add(cell.toString().trim());
        return result.toArray(new String[0]);
    }

    @PostMapping("/delete")
    public String delete(@RequestParam Long id, HttpServletRequest request) {
        Subject subject = SecurityUtils.getSubject();
        User user = (User) subject.getPrincipal();
        hostService.removeById(id);
        auditLogService.log(user.getId(), user.getUsername(), "host.delete", "host", String.valueOf(id), null, getClientIp(request), request.getHeader("User-Agent"));
        return "redirect:/host";
    }

    /** 切换主机启用/禁用状态。仅允许有权限的主机。 */
    @PostMapping("/toggle-status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleStatus(@RequestParam Long id, HttpServletRequest request) {
        Subject subject = SecurityUtils.getSubject();
        User user = (User) subject.getPrincipal();
        List<Host> allowed = hostService.listForUserIncludeDisabled(user.getId());
        if (!allowed.stream().anyMatch(h -> id.equals(h.getId()))) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "无权限"));
        }
        Host h = hostService.getById(id);
        if (h == null) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "主机不存在"));
        }
        int next = (h.getStatus() != null && h.getStatus() == 1) ? 0 : 1;
        h.setStatus(next);
        hostService.updateById(h);
        hostListCacheService.refreshUser(user.getId());
        String action = next == 1 ? "host.enable" : "host.disable";
        auditLogService.log(user.getId(), user.getUsername(), action, "host", String.valueOf(id), h.getName(), getClientIp(request), request.getHeader("User-Agent"));
        return ResponseEntity.ok(Map.of("success", true, "status", next));
    }

    /** 批量删除主机 */
    @PostMapping("/batch-delete")
    public String batchDelete(@RequestParam("ids") List<Long> ids, HttpServletRequest request) {
        Subject subject = SecurityUtils.getSubject();
        User user = (User) subject.getPrincipal();
        if (ids != null && !ids.isEmpty()) {
            for (Long id : ids) {
                if (id != null) {
                    hostService.removeById(id);
                    auditLogService.log(user.getId(), user.getUsername(), "host.delete", "host", String.valueOf(id), null, getClientIp(request), request.getHeader("User-Agent"));
                }
            }
        }
        return "redirect:/host";
    }

    /** 批量设置分组：仅操作当前用户有权限的主机 */
    @PostMapping("/batch-set-group")
    public String batchSetGroup(@RequestParam("ids") List<Long> ids,
                                @RequestParam(value = "groupId", required = false) Long groupId,
                                HttpServletRequest request) {
        Subject subject = SecurityUtils.getSubject();
        User user = (User) subject.getPrincipal();
        List<Host> allowed = hostService.listForUserIncludeDisabled(user.getId());
        java.util.Set<Long> allowedIds = allowed.stream().map(Host::getId).filter(id -> id != null).collect(Collectors.toSet());
        List<Long> toUpdate = ids != null ? ids.stream().filter(id -> id != null && allowedIds.contains(id)).distinct().collect(Collectors.toList()) : List.of();
        if (!toUpdate.isEmpty()) {
            hostService.batchSetGroupIds(toUpdate, groupId);
            auditLogService.log(user.getId(), user.getUsername(), "host.batch_set_group", "host", String.valueOf(toUpdate.size()) + "台", null, getClientIp(request), request.getHeader("User-Agent"));
        }
        return "redirect:/host";
    }

    /** 批量设置监控规则：仅操作当前用户有权限的主机 */
    @PostMapping("/batch-set-rules")
    public String batchSetRules(@RequestParam("ids") List<Long> ids,
                                @RequestParam(value = "ruleIds", required = false) List<Long> ruleIds,
                                HttpServletRequest request) {
        Subject subject = SecurityUtils.getSubject();
        User user = (User) subject.getPrincipal();
        List<Host> allowed = hostService.listForUserIncludeDisabled(user.getId());
        java.util.Set<Long> allowedIds = allowed.stream().map(Host::getId).filter(id -> id != null).collect(Collectors.toSet());
        List<Long> toUpdate = ids != null ? ids.stream().filter(id -> id != null && allowedIds.contains(id)).distinct().collect(Collectors.toList()) : List.of();
        List<Long> rids = ruleIds != null ? ruleIds : List.of();
        for (Long hostId : toUpdate) {
            hostAlertRuleService.setRulesForHost(hostId, rids);
        }
        if (!toUpdate.isEmpty()) {
            auditLogService.log(user.getId(), user.getUsername(), "host.batch_set_rules", "host", String.valueOf(toUpdate.size()) + "台", null, getClientIp(request), request.getHeader("User-Agent"));
        }
        return "redirect:/host";
    }

    /** 批量设置认证方式：仅操作当前用户有权限的主机。选密码时可选填 password，填写则统一设置该密码。 */
    @PostMapping("/batch-set-auth")
    public String batchSetAuth(@RequestParam("ids") List<Long> ids,
                               @RequestParam String authType,
                               @RequestParam(required = false) Long sshKeyId,
                               @RequestParam(required = false) String password,
                               HttpServletRequest request) {
        Subject subject = SecurityUtils.getSubject();
        User user = (User) subject.getPrincipal();
        List<Host> allowed = hostService.listForUserIncludeDisabled(user.getId());
        java.util.Set<Long> allowedIds = allowed.stream().map(Host::getId).filter(id -> id != null).collect(Collectors.toSet());
        List<Long> toUpdate = ids != null ? ids.stream().filter(id -> id != null && allowedIds.contains(id)).distinct().collect(Collectors.toList()) : List.of();
        if (!toUpdate.isEmpty()) {
            Long keyId = "privateKey".equals(authType) ? sshKeyId : null;
            hostService.batchSetAuth(toUpdate, authType != null ? authType : "password", keyId, password);
            auditLogService.log(user.getId(), user.getUsername(), "host.batch_set_auth", "host", String.valueOf(toUpdate.size()) + "台", null, getClientIp(request), request.getHeader("User-Agent"));
        }
        return "redirect:/host";
    }

    private static String getClientIp(HttpServletRequest request) {
        String x = request.getHeader("X-Forwarded-For");
        if (x != null && !x.isEmpty()) return x.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
