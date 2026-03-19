package com.vti.vops.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vti.vops.entity.AlertEvent;
import com.vti.vops.entity.AlertRule;
import com.vti.vops.entity.Host;
import com.vti.vops.entity.User;
import com.vti.vops.mapper.AlertEventMapper;
import com.vti.vops.service.IAlertRuleService;
import com.vti.vops.service.IHostService;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.SecurityUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 告警事件（触发发送告警后的记录）列表，按当前用户有权限的主机过滤。
 */
@Controller
@RequestMapping("/alert/events")
@RequiredArgsConstructor
public class AlertEventController {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final AlertEventMapper alertEventMapper;
    private final IHostService hostService;
    private final IAlertRuleService alertRuleService;

    @GetMapping
    public String list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer status,
            Model model) {
        User user = (User) SecurityUtils.getSubject().getPrincipal();
        if (user == null) {
            return "redirect:/login";
        }
        List<Host> allowedHosts = hostService.listForUser(user.getId());
        List<Long> allowedHostIds = allowedHosts.stream()
                .map(Host::getId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (allowedHostIds.isEmpty()) {
            model.addAttribute("events", Collections.emptyList());
            model.addAttribute("total", 0L);
            model.addAttribute("currentPage", 1);
            model.addAttribute("totalPages", 0);
            model.addAttribute("pageSize", size);
            model.addAttribute("statusFilter", status);
            model.addAttribute("ruleIdToName", Collections.emptyMap());
            model.addAttribute("hostIdToName", Collections.emptyMap());
            return "alert/events";
        }
        if (page < 1) page = 1;
        if (size < 1) size = DEFAULT_SIZE;
        if (size > MAX_SIZE) size = MAX_SIZE;
        LambdaQueryWrapper<AlertEvent> wrapper = new LambdaQueryWrapper<AlertEvent>()
                .in(AlertEvent::getHostId, allowedHostIds)
                .orderByDesc(AlertEvent::getCreateTime);
        if (status != null) {
            wrapper.eq(AlertEvent::getStatus, status);
        }
        Page<AlertEvent> p = new Page<>(page, size);
        alertEventMapper.selectPage(p, wrapper);
        List<AlertEvent> events = p.getRecords();
        Set<Long> ruleIds = events.stream().map(AlertEvent::getRuleId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> hostIds = events.stream().map(AlertEvent::getHostId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, String> ruleIdToName = new HashMap<>();
        if (!ruleIds.isEmpty()) {
            for (AlertRule r : alertRuleService.listByIds(ruleIds)) {
                if (r.getId() != null) ruleIdToName.put(String.valueOf(r.getId()), r.getName() != null ? r.getName() : "");
            }
        }
        Map<String, String> hostIdToName = new HashMap<>();
        if (!hostIds.isEmpty()) {
            for (Host h : hostService.listByIds(hostIds)) {
                if (h.getId() != null) {
                    String name = h.getName() != null ? h.getName() : (h.getHostname() != null ? h.getHostname() : "");
                    hostIdToName.put(String.valueOf(h.getId()), name);
                }
            }
        }
        model.addAttribute("events", events);
        model.addAttribute("total", p.getTotal());
        model.addAttribute("currentPage", p.getCurrent());
        model.addAttribute("totalPages", p.getPages());
        model.addAttribute("pageSize", size);
        model.addAttribute("statusFilter", status);
        model.addAttribute("ruleIdToName", ruleIdToName);
        model.addAttribute("hostIdToName", hostIdToName);
        return "alert/events";
    }
}
