package com.vti.vops.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vti.vops.dto.HostListDataVo;
import com.vti.vops.dto.HostListMetaVo;
import com.vti.vops.dto.HostStatusVo;
import com.vti.vops.entity.Host;
import com.vti.vops.entity.HostMetric;
import com.vti.vops.entity.User;
import com.vti.vops.mapper.UserMapper;
import com.vti.vops.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 主机列表缓存：按用户缓存 hosts + 指标状态 + 规则数，全局缓存 groups/alertRules/sshKeys，定时刷新。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HostListCacheServiceImpl implements IHostListCacheService {

    private static final long ONLINE_THRESHOLD_MS = 10 * 60 * 1000L;

    private final IHostService hostService;
    private final IHostMetricService hostMetricService;
    private final IHostAlertRuleService hostAlertRuleService;
    private final IAlertRuleService alertRuleService;
    private final ISshKeyService sshKeyService;
    private final UserMapper userMapper;
    private final CacheManager cacheManager;

    @Override
    @Cacheable(value = "hostListData", key = "#userId")
    public HostListDataVo getHostListData(Long userId) {
        return computeHostListData(userId);
    }

    @Override
    @Cacheable(value = "hostListMeta", key = "'global'")
    public HostListMetaVo getHostListMeta() {
        return computeHostListMeta();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        Thread t = new Thread(() -> {
            try {
                log.info("Startup: pre-warming host list cache");
                refreshAll();
                log.info("Startup: host list cache pre-warm done");
            } catch (Exception e) {
                log.warn("Startup: host list pre-warm failed: {}", e.getMessage());
            }
        }, "host-list-pre-warm");
        t.setDaemon(false);
        t.start();
    }

    @Override
    @Scheduled(fixedDelayString = "${vops.host-list-cache-refresh-interval-ms:45000}")
    @Async
    public void refreshAll() {
        var dataCache = cacheManager.getCache("hostListData");
        var metaCache = cacheManager.getCache("hostListMeta");
        if (metaCache != null) {
            metaCache.put("global", computeHostListMeta());
        }
        if (dataCache == null) return;
        List<Long> userIds = userMapper.selectList(
                new LambdaQueryWrapper<User>().select(User::getId))
                .stream()
                .map(User::getId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        for (Long userId : userIds) {
            try {
                dataCache.put(userId, computeHostListData(userId));
            } catch (Exception e) {
                log.debug("Host list cache refresh for user {} failed: {}", userId, e.getMessage());
            }
        }
    }

    @Override
    public void refreshUser(Long userId) {
        if (userId == null) return;
        var dataCache = cacheManager.getCache("hostListData");
        if (dataCache == null) return;
        try {
            dataCache.put(userId, computeHostListData(userId));
        } catch (Exception e) {
            log.debug("Host list cache refresh for user {} failed: {}", userId, e.getMessage());
        }
    }

    private HostListDataVo computeHostListData(Long userId) {
        HostListDataVo vo = new HostListDataVo();
        List<Host> hosts = hostService.listForUserIncludeDisabled(userId);
        List<Long> hostIds = hosts.stream().map(Host::getId).filter(Objects::nonNull).collect(Collectors.toList());
        List<HostMetric> latestList = hostMetricService.listLatestByHostIds(hostIds, false);
        Map<Long, HostStatusVo> statusMap = latestList.stream()
                .collect(Collectors.toMap(HostMetric::getHostId, this::fromMetric, (a, b) -> a));
        for (Host h : hosts) {
            if (h.getId() != null && !statusMap.containsKey(h.getId())) {
                statusMap.put(h.getId(), HostStatusVo.builder().online(false).build());
            }
        }
        Map<Long, Integer> hostIdToRuleCount = hostIds.isEmpty() ? Map.of()
                : hostAlertRuleService.listRuleCountByHostIds(hostIds);
        vo.setHosts(hosts);
        vo.setStatusMap(statusMap);
        vo.setHostIdToRuleCount(hostIdToRuleCount);
        return vo;
    }

    private HostListMetaVo computeHostListMeta() {
        HostListMetaVo vo = new HostListMetaVo();
        vo.setGroups(hostService.listGroups());
        vo.setAlertRules(alertRuleService.list());
        vo.setSshKeys(sshKeyService.listNames());
        return vo;
    }

    private HostStatusVo fromMetric(HostMetric m) {
        long now = System.currentTimeMillis();
        long collectMs = m.getCollectTime() != null ? m.getCollectTime().getTime() : 0;
        boolean recent = (now - collectMs) <= ONLINE_THRESHOLD_MS;
        boolean icmpReachable = m.getIcmpRttMs() == null || m.getIcmpRttMs() < HostMetric.ICMP_UNREACHABLE_RTT_MS;
        boolean online = recent && icmpReachable;
        long diskUsed = m.getDiskUsed() != null ? m.getDiskUsed() : 0;
        long diskTotal = m.getDiskTotal() != null ? m.getDiskTotal() : 0;
        return HostStatusVo.builder()
                .online(online)
                .cpuUsage(m.getCpuUsage())
                .cpuCores(m.getCpuCores())
                .loadAvg(m.getLoadAvg())
                .memUsagePercent(m.getMemUsagePercent())
                .memUsed(m.getMemUsed() != null ? m.getMemUsed() : 0L)
                .memTotal(m.getMemTotal() != null ? m.getMemTotal() : 0L)
                .memUsedFormatted(m.getMemUsedFormatted())
                .memTotalFormatted(m.getMemTotalFormatted())
                .diskUsagePercent(m.getDiskUsagePercent())
                .diskUsedFormatted(diskUsed > 0 ? formatBytes(diskUsed) : null)
                .diskTotalFormatted(diskTotal > 0 ? formatBytes(diskTotal) : null)
                .build();
    }

    private static String formatBytes(long bytes) {
        if (bytes <= 0) return "0";
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fK", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1fM", bytes / (1024.0 * 1024));
        if (bytes < 1024L * 1024 * 1024 * 1024) return String.format("%.1fG", bytes / (1024.0 * 1024 * 1024));
        return String.format("%.1fT", bytes / (1024.0 * 1024 * 1024 * 1024));
    }
}
