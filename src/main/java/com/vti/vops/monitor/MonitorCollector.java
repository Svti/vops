package com.vti.vops.monitor;

import com.vti.vops.alert.AlertRuleEngine;
import com.vti.vops.entity.Host;
import com.vti.vops.entity.HostMetric;
import com.vti.vops.monitor.LinuxMetricParser.MemInfo;
import com.vti.vops.service.IHostMetricService;
import com.vti.vops.service.IHostService;
import com.vti.vops.ssh.SshClient;
import com.vti.vops.ssh.SshConnectionPool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 定时通过 SSH 采集 Linux 指标，写入 MySQL 历史与内存实时缓存
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonitorCollector {

    private final IHostService hostService;
    private final IHostMetricService hostMetricService;
    private final SshConnectionPool sshPool;
    private final AlertRuleEngine alertRuleEngine;

    /** 每主机采集状态：CPU 上轮采样、网络上轮 rx/tx/时间、默认网卡名 */
    private final Map<Long, HostCollectState> hostState = new ConcurrentHashMap<>();

    /** 采集并发线程数，最大 16 */
    private static final int COLLECT_MAX_PARALLEL = 16;

	/** 采集 cron，默认每 3 分钟。可在 application.properties 中配置 vops.monitor.collect-cron 覆盖。采用异步并发采集。 */
    @Scheduled(cron = "${vops.monitor.collect-cron:0 0/3 * * * ?}")
    public void collect() {
        List<Host> hosts = hostService.listAllEnabled();
        if (hosts == null || hosts.isEmpty()) return;
        int poolSize = Math.min(hosts.size(), COLLECT_MAX_PARALLEL);
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        try {
            for (Host host : hosts) {
                Long hostId = host.getId();
                if (hostId == null) continue;
                executor.submit(() -> {
                    try {
                        collectOne(hostId);
                    } catch (Exception e) {
                        log.warn("Collect failed hostId={}: {}", hostId, e.getMessage());
                    }
                });
            }
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.MINUTES)) {
                    executor.shutdownNow();
                    if (!executor.awaitTermination(1, TimeUnit.MINUTES))
                        log.warn("Monitor collect executor did not terminate");
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public void collectOne(Long hostId) {
        java.util.Optional<SshClient> clientOpt = sshPool.getClient(hostId);
        for (int retry = 0; clientOpt.isEmpty() && retry < 2; retry++) {
            int delayMs = 2000 + retry * 1000;
            log.warn("Collect hostId={}: SSH connection unavailable, retry {}/2 in {}ms", hostId, retry + 1, delayMs);
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
            clientOpt = sshPool.getClient(hostId);
        }
        if (clientOpt.isEmpty()) {
            log.warn("Collect hostId={} skipped: SSH connection unavailable after retries (no metric saved this round)", hostId);
            return;
        }
        SshClient client = clientOpt.get();
        try {
            doCollectOne(hostId, client);
        } catch (Exception e) {
            log.warn("Collect hostId={} failed: {} (invalidating connection and retrying once)", hostId, e.getMessage());
            sshPool.invalidate(hostId);
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            clientOpt = sshPool.getClient(hostId);
            if (clientOpt.isEmpty()) {
                log.warn("Collect hostId={} retry failed: could not get new connection (no metric saved this round)", hostId);
                return;
            }
            try {
                doCollectOne(hostId, clientOpt.get());
            } catch (Exception e2) {
                log.warn("Collect hostId={} retry failed: {} (no metric saved this round)", hostId, e2.getMessage());
            } finally {
                sshPool.release(hostId);
            }
        } finally {
            sshPool.release(hostId);
        }
    }

    private void doCollectOne(Long hostId, SshClient client) throws Exception {
        String stat = client.exec("cat /proc/stat | head -1; sleep 1; cat /proc/stat | head -1", 8);
        HostCollectState state = hostState.computeIfAbsent(hostId, k -> new HostCollectState());
        Map<String, long[]> cpuPrev = state.cpuPrev;
        double cpuUsage = 0;
        if (stat != null && !stat.isEmpty()) {
            for (String ln : stat.split("\n")) {
                String trimmed = ln.trim();
                if (trimmed.startsWith("cpu")) {
                    cpuUsage = LinuxMetricParser.parseCpuUsage(trimmed, cpuPrev);
                }
            }
        }
        String freeOut = client.exec("free -b", 5);
        String dfOut = client.exec("df -k", 10);
        String iface = state.iface;
        if (iface == null || iface.isBlank()) {
            try {
                iface = detectDefaultInterface(client);
                if (iface != null && !iface.isBlank()) state.iface = iface;
            } catch (Exception e) {
                log.debug("Detect interface hostId={}: {}", hostId, e.getMessage());
            }
        }
        long rxBytes = 0, txBytes = 0;
        if (iface != null && !iface.isBlank()) {
            try {
                rxBytes = readRxBytes(client, iface);
                txBytes = readTxBytes(client, iface);
            } catch (Exception e) {
                log.debug("Read /sys net stats hostId={} iface={}: {}", hostId, iface, e.getMessage());
                state.iface = null;
            }
        }
        long[] netSum = new long[]{rxBytes, txBytes};
        String psOut = client.exec("ps aux 2>/dev/null | wc -l", 5);
        String psTopOut = client.exec("ps -eo pid,user,pcpu,pmem,comm --no-headers --sort=-pcpu 2>/dev/null | head -5", 5);
        String nprocOut = client.exec("nproc 2>/dev/null || echo 0", 3);
        String loadavgOut = client.exec("cat /proc/loadavg 2>/dev/null || echo '0 0 0'", 3);
        String uptimeOut = client.exec("cat /proc/uptime 2>/dev/null || echo 0", 3);

        MemInfo mem = LinuxMetricParser.parseMem(freeOut);
        String diskJson = LinuxMetricParser.parseDf(dfOut);
        long nowMs = System.currentTimeMillis();
        Long networkRxRateBps = null;
        Long networkTxRateBps = null;
        long[] prev = state.networkPrev;
        if (prev != null && prev[2] > 0) {
            long elapsedSec = Math.max(1, (nowMs - prev[2]) / 1000);
            networkRxRateBps = Math.max(0L, (netSum[0] - prev[0]) / elapsedSec);
            networkTxRateBps = Math.max(0L, (netSum[1] - prev[1]) / elapsedSec);
        }
        state.networkPrev = new long[]{netSum[0], netSum[1], nowMs};

        String processSummary = LinuxMetricParser.parseProcessSummary(psOut);
        String processTopCpu = LinuxMetricParser.parseProcessTopCpu(psTopOut);
        int cpuCores = parseCpuCores(nprocOut);
        String loadAvg = parseLoadAvg(loadavgOut);
        Long uptimeSeconds = parseUptimeSeconds(uptimeOut);
        long rootDiskTotal = parseRootDiskTotal(diskJson);
        long rootDiskUsed = parseRootDiskUsed(diskJson);

        int diskUsagePercent = parseRootDiskUsagePercent(diskJson);

        HostMetric metric = new HostMetric();
        metric.setHostId(hostId);
        metric.setCollectTime(new Date());
        metric.setCpuUsage(cpuUsage);
        metric.setCpuCores(cpuCores);
        metric.setLoadAvg(loadAvg);
        metric.setUptimeSeconds(uptimeSeconds);
        metric.setMemTotal(mem.getTotal());
        metric.setMemUsed(mem.getUsed());
        metric.setMemFree(mem.getFree());
        metric.setMemUsagePercent(mem.getUsagePercent());
        metric.setDiskUsagePercent(diskUsagePercent);
        metric.setDiskTotal(rootDiskTotal);
        metric.setDiskUsed(rootDiskUsed);
        metric.setDiskJson(diskJson);
        metric.setNetworkRxRateBps(networkRxRateBps);
        metric.setNetworkTxRateBps(networkTxRateBps);
        metric.setProcessSummary(processSummary);
        metric.setProcessTopCpu(processTopCpu);
        hostMetricService.getLatestByHostId(hostId).map(HostMetric::getIcmpRttMs).ifPresent(metric::setIcmpRttMs);

        hostMetricService.save(metric);
        Host hostUpdate = new Host();
        hostUpdate.setId(hostId);
        hostUpdate.setLastMetricTime(metric.getCollectTime());
        hostService.updateById(hostUpdate);
        alertRuleEngine.evaluate(hostId, metric);
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private static int parseCpuCores(String nprocOut) {
        if (nprocOut == null) return 0;
        try {
            return Math.max(0, Integer.parseInt(nprocOut.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 解析 /proc/loadavg 前三个数：1/5/15 分钟平均负载 */
    private static String parseLoadAvg(String loadavgOut) {
        if (loadavgOut == null || loadavgOut.isEmpty()) return "";
        String[] parts = loadavgOut.trim().split("\\s+");
        if (parts.length >= 3) return parts[0] + " " + parts[1] + " " + parts[2];
        return loadavgOut.trim();
    }

    /** Step 1 — 获取默认网卡：ip route show default 解析 dev xxx，无则取第一个非 lo 网卡（BusyBox 可用） */
    private static String detectDefaultInterface(SshClient client) throws Exception {
        String out = client.exec("ip route show default", 5);
        if (out != null && !out.isBlank()) {
            String[] tokens = out.trim().split("\\s+");
            for (int i = 0; i < tokens.length - 1; i++) {
                if ("dev".equals(tokens[i])) {
                    String iface = tokens[i + 1].trim();
                    if (!iface.isEmpty()) return iface;
                }
            }
        }
        String fallback = client.exec("ls /sys/class/net | grep -v lo | head -n1", 5);
        if (fallback != null && !fallback.isBlank()) return fallback.trim();
        throw new RuntimeException("Cannot detect network interface");
    }

    /** Step 2 — 只读 /sys，无解析 */
    private static long readRxBytes(SshClient client, String iface) throws Exception {
        String out = client.exec("cat /sys/class/net/" + iface + "/statistics/rx_bytes", 5);
        return Long.parseLong(out != null ? out.trim() : "0");
    }

    private static long readTxBytes(SshClient client, String iface) throws Exception {
        String out = client.exec("cat /sys/class/net/" + iface + "/statistics/tx_bytes", 5);
        return Long.parseLong(out != null ? out.trim() : "0");
    }

    /** 解析 /proc/uptime 第一个数为运行秒数 */
    private static Long parseUptimeSeconds(String uptimeOut) {
        if (uptimeOut == null || uptimeOut.isEmpty()) return null;
        String[] parts = uptimeOut.trim().split("\\s+");
        if (parts.length < 1) return null;
        try {
            double sec = Double.parseDouble(parts[0].trim());
            return sec >= 0 ? (long) sec : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 从 diskJson 数组中取挂载点为 / 的 total（字节），若无则取第一项 */
    private static long parseRootDiskTotal(String diskJson) {
        if (diskJson == null || diskJson.isEmpty()) return 0;
        try {
            JsonNode arr = JSON.readTree(diskJson);
            if (!arr.isArray()) return 0;
            for (JsonNode node : arr) {
                if ("/".equals(node.path("mount").asText("").trim())) {
                    return node.path("total").asLong(0);
                }
            }
            if (arr.size() > 0 && arr.get(0).has("total")) {
                return arr.get(0).path("total").asLong(0);
            }
        } catch (Exception ignored) { }
        return 0;
    }

    /** 从 diskJson 数组中取挂载点为 / 的 used（字节），若无则取第一项 */
    private static long parseRootDiskUsed(String diskJson) {
        if (diskJson == null || diskJson.isEmpty()) return 0;
        try {
            JsonNode arr = JSON.readTree(diskJson);
            if (!arr.isArray()) return 0;
            for (JsonNode node : arr) {
                if ("/".equals(node.path("mount").asText("").trim())) {
                    return node.path("used").asLong(0);
                }
            }
            if (arr.size() > 0 && arr.get(0).has("used")) {
                return arr.get(0).path("used").asLong(0);
            }
        } catch (Exception ignored) { }
        return 0;
    }

    /** 从 diskJson 数组中取挂载点为 / 的 usePercent，若无则取第一项 */
    private static int parseRootDiskUsagePercent(String diskJson) {
        if (diskJson == null || diskJson.isEmpty()) return 0;
        try {
            JsonNode arr = JSON.readTree(diskJson);
            if (!arr.isArray()) return 0;
            for (JsonNode node : arr) {
                if ("/".equals(node.path("mount").asText("").trim())) {
                    return node.path("usePercent").asInt(0);
                }
            }
            if (arr.size() > 0 && arr.get(0).has("usePercent")) {
                return arr.get(0).path("usePercent").asInt(0);
            }
        } catch (Exception ignored) { }
        return 0;
    }

    /** 单主机采集状态：CPU 上轮采样、网络上轮 [rx,tx,timeMs]、默认网卡名 */
    private static class HostCollectState {
        final Map<String, long[]> cpuPrev = new ConcurrentHashMap<>();
        long[] networkPrev;
        String iface;
    }
}
