package com.vti.vops.controller;

import com.vti.vops.entity.HostGroup;
import com.vti.vops.entity.Host;
import com.vti.vops.entity.HostMetric;
import com.vti.vops.entity.User;
import com.vti.vops.service.IHostMetricService;
import com.vti.vops.service.IHostService;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/monitor")
@RequiredArgsConstructor
public class MonitorController {

    private final IHostService hostService;
    private final IHostMetricService hostMetricService;

    @GetMapping
    public String dashboard(@RequestParam(required = false) Long hostId, Model model) {
        User user = (User) SecurityUtils.getSubject().getPrincipal();
        List<Host> hosts = hostService.listForUser(user.getId());
        model.addAttribute("hosts", hosts);

        String pageTitle = "监控中心";
        if (hostId != null) {
            Host selected = hosts.stream()
                    .filter(h -> h.getId() != null && h.getId().equals(hostId))
                    .findFirst()
                    .orElse(null);
            if (selected != null) {
                String hostName = selected.getName() != null && !selected.getName().isBlank()
                        ? selected.getName()
                        : (selected.getHostname() != null && !selected.getHostname().isBlank()
                        ? selected.getHostname()
                        : String.valueOf(hostId));
                pageTitle = "监控中心 - " + hostName;
            }
        }
        model.addAttribute("pageTitle", pageTitle);

        Map<String, String> groupIdToName = new HashMap<>();
        for (HostGroup g : hostService.listGroups()) {
            if (g.getId() != null) groupIdToName.put(String.valueOf(g.getId()), g.getName() != null ? g.getName() : "");
        }
        model.addAttribute("groupIdToName", groupIdToName);
        return "monitor/dashboard";
    }

    @GetMapping("/api/history/{hostId}")
    @ResponseBody
    public ResponseEntity<?> metricHistory(
            @PathVariable Long hostId,
            @RequestParam(defaultValue = "1") int hours
    ) {
        User user = (User) SecurityUtils.getSubject().getPrincipal();
        if (!hostService.listForUser(user.getId()).stream().anyMatch(h -> hostId.equals(h.getId()))) {
            return ResponseEntity.status(403).build();
        }
        Date end = new Date();
        Date start = new Date(end.getTime() - hours * 3600L * 1000);
        return ResponseEntity.ok(hostMetricService.listByHostIdAndTimeRangeForChart(hostId, start, end));
    }

    /** 单台主机最新一条采集记录（复用批量逻辑，无权限或无数据时 204） */
    @GetMapping("/api/latest/{hostId}")
    @ResponseBody
    public ResponseEntity<?> latestMetric(@PathVariable Long hostId) {
        List<HostMetric> list = listLatestByUser(List.of(hostId));
        return list.isEmpty()
                ? ResponseEntity.noContent().build()
                : ResponseEntity.ok(list.get(0));
    }

    /** 批量获取多台主机最新一条采集记录（监控中心首页卡片用，按需只查卡片所需列）。POST 推荐；GET 兼容旧调用（?hostIds=1,2,3） */
    @PostMapping("/api/latest")
    @ResponseBody
    public ResponseEntity<?> latestMetricsBatch(@RequestBody List<Long> hostIds) {
        return ResponseEntity.ok(listLatestByUserForCards(hostIds));
    }

    @GetMapping(value = "/api/latest", params = "hostIds")
    @ResponseBody
    public ResponseEntity<?> latestMetricsBatchGet(@RequestParam String hostIds) {
        List<Long> ids = Arrays.stream(hostIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .toList();
        return ResponseEntity.ok(listLatestByUserForCards(ids));
    }

    /** 按当前用户权限过滤后查询最新指标（详情页用，全列） */
    private List<HostMetric> listLatestByUser(List<Long> hostIds) {
        if (hostIds == null || hostIds.isEmpty()) {
            return List.of();
        }
        User user = (User) SecurityUtils.getSubject().getPrincipal();
        List<Long> allowedIds = hostService.listForUser(user.getId()).stream()
                .filter(h -> h.getId() != null && hostIds.contains(h.getId()))
                .map(h -> h.getId())
                .toList();
        if (allowedIds.isEmpty()) {
            return List.of();
        }
        return hostMetricService.listLatestByHostIds(allowedIds, false);
    }

    /** 按当前用户权限过滤后查询最新指标（监控首页卡片用，只查卡片所需列） */
    private List<HostMetric> listLatestByUserForCards(List<Long> hostIds) {
        if (hostIds == null || hostIds.isEmpty()) {
            return List.of();
        }
        User user = (User) SecurityUtils.getSubject().getPrincipal();
        List<Long> allowedIds = hostService.listForUser(user.getId()).stream()
                .filter(h -> h.getId() != null && hostIds.contains(h.getId()))
                .map(h -> h.getId())
                .toList();
        if (allowedIds.isEmpty()) {
            return List.of();
        }
        return hostMetricService.listLatestByHostIds(allowedIds, true);
    }
}
