package com.vti.vops.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vti.vops.dto.IndexSummaryVo;
import com.vti.vops.entity.Host;
import com.vti.vops.entity.HostMetric;
import com.vti.vops.entity.User;
import com.vti.vops.mapper.BatchScheduleMapper;
import com.vti.vops.mapper.UserMapper;
import com.vti.vops.service.IIndexSummaryService;
import com.vti.vops.service.IAlertRuleService;
import com.vti.vops.service.IHostMetricService;
import com.vti.vops.service.IHostService;
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
 * 首页运维总览：按用户缓存，定时异步刷新所有用户数据，打开首页时直接读缓存无需等待。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndexSummaryServiceImpl implements IIndexSummaryService {

    private static final long ONLINE_THRESHOLD_MS = 10 * 60 * 1000L;

    private final IHostService hostService;
    private final IHostMetricService hostMetricService;
    private final IAlertRuleService alertRuleService;
    private final BatchScheduleMapper batchScheduleMapper;
    private final UserMapper userMapper;
    private final CacheManager cacheManager;

    @Override
    @Cacheable(value = "indexSummary", key = "#userId")
    public IndexSummaryVo getSummary(Long userId) {
        return computeSummary(userId);
    }

    /** 应用就绪后异步预刷新一次，使首页数据在后台就绪，打开时直接读缓存 */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        Thread t = new Thread(() -> {
            try {
                log.info("Startup: pre-warming index summary cache for all users");
                refreshAll();
                log.info("Startup: index summary cache pre-warm done");
            } catch (Exception e) {
                log.warn("Startup: index summary pre-warm failed: {}", e.getMessage());
            }
        }, "index-summary-pre-warm");
        t.setDaemon(false);
        t.start();
    }

    @Override
    @Scheduled(cron = "0/5 * * * * ?")
    @Async
    public void refreshAll() {
        var cache = cacheManager.getCache("indexSummary");
        if (cache == null) return;
        List<Long> userIds = userMapper.selectList(
                new LambdaQueryWrapper<User>().select(User::getId))
                .stream()
                .map(User::getId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        for (Long userId : userIds) {
            try {
                cache.put(userId, computeSummary(userId));
            } catch (Exception e) {
                log.debug("Index summary refresh for user {} failed: {}", userId, e.getMessage());
            }
        }
    }

    /** 实际计算逻辑，供 getSummary 与 refreshAll 使用 */
    IndexSummaryVo computeSummary(Long userId) {
        IndexSummaryVo vo = new IndexSummaryVo();
        List<Host> hosts = hostService.listForUser(userId);
        List<Long> hostIds = hosts.stream().map(Host::getId).filter(Objects::nonNull).collect(Collectors.toList());
        List<HostMetric> latestList = hostMetricService.listLatestByHostIds(hostIds, false);
        Map<Long, HostMetric> metricByHost = latestList.stream().collect(Collectors.toMap(HostMetric::getHostId, m -> m, (a, b) -> a));

        int totalHosts = hosts.size();
        int onlineHosts = 0;
        double cpuSum = 0;
        int cpuCount = 0;
        double memSum = 0;
        int memCount = 0;
        List<Map<String, Object>> topHostsByCpu = new ArrayList<>();
        List<Map<String, Object>> topHostsByMem = new ArrayList<>();

        for (Host h : hosts) {
            HostMetric m = h.getId() != null ? metricByHost.get(h.getId()) : null;
            long now = System.currentTimeMillis();
            long collectMs = m != null && m.getCollectTime() != null ? m.getCollectTime().getTime() : 0;
            boolean recent = (now - collectMs) <= ONLINE_THRESHOLD_MS;
            boolean icmpReachable = m == null || m.getIcmpRttMs() == null || m.getIcmpRttMs() < HostMetric.ICMP_UNREACHABLE_RTT_MS;
            boolean online = recent && icmpReachable;
            if (online) onlineHosts++;
            Double cpu = m != null ? m.getCpuUsage() : null;
            if (cpu != null) {
                cpuSum += cpu;
                cpuCount++;
            }
            Double mem = m != null ? m.getMemUsagePercent() : null;
            if (mem != null) {
                memSum += mem;
                memCount++;
            }
            if (online && (cpu != null || mem != null)) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("hostId", h.getId());
                row.put("name", h.getName());
                row.put("cpu", cpu != null ? cpu : 0.0);
                row.put("mem", mem != null ? mem : 0.0);
                row.put("online", true);
                topHostsByCpu.add(row);
                topHostsByMem.add(new LinkedHashMap<>(row));
            }
        }
        topHostsByCpu.sort((a, b) -> Double.compare((Double) b.get("cpu"), (Double) a.get("cpu")));
        if (topHostsByCpu.size() > 5) topHostsByCpu = new ArrayList<>(topHostsByCpu.subList(0, 5));
        topHostsByMem.sort((a, b) -> Double.compare((Double) b.get("mem"), (Double) a.get("mem")));
        if (topHostsByMem.size() > 5) topHostsByMem = new ArrayList<>(topHostsByMem.subList(0, 5));

        vo.setTotalHosts(totalHosts);
        vo.setOnlineHosts(onlineHosts);
        vo.setAvgCpu(cpuCount > 0 ? Math.round(cpuSum / cpuCount * 100.0) / 100.0 : 0.0);
        vo.setAvgMem(memCount > 0 ? Math.round(memSum / memCount * 100.0) / 100.0 : 0.0);
        vo.setTotalAlertRules(alertRuleService.count());
        vo.setTotalBatchTasks(batchScheduleMapper.selectCount(null));
        vo.setTopHostsByCpu(topHostsByCpu);
        vo.setTopHostsByMem(topHostsByMem);
        return vo;
    }
}
