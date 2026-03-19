package com.vti.vops.monitor;

import com.vti.vops.alert.AlertRuleEngine;
import com.vti.vops.entity.Host;
import com.vti.vops.entity.HostMetric;
import com.vti.vops.service.IHostMetricService;
import com.vti.vops.service.IHostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 定时从本机对主机执行 ICMP ping，采集连通性与 RTT，更新 host_metric 并参与告警评估。
 * 不可达时写入 icmpRttMs=9999（HostMetric.ICMP_UNREACHABLE_RTT_MS），告警规则可用 metricKey=icmp_rtt_ms, operator=gte, threshold=9999。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IcmpCollector {

    private static final int PING_TIMEOUT_SEC = 4;
    private static final int COLLECT_PARALLEL = 8;
    /** Linux/macOS: time=1.23 ms 或 time=1.23ms；BSD round-trip: 1.2/1.3/1.4 ms 取第一个数 */
    private static final Pattern RTT_PATTERN = Pattern.compile("time[=<>]([\\d.]+)\\s*ms|(?:round-trip|min/avg/max)[^\\d]*([\\d.]+)", Pattern.CASE_INSENSITIVE);

    private final IHostService hostService;
    private final IHostMetricService hostMetricService;
    private final AlertRuleEngine alertRuleEngine;

    /** ICMP 独立调度，默认每 1 分钟（比 SSH 快、开销小，延迟数据更及时）。可配置 vops.monitor.icmp-cron。 */
    @Scheduled(cron = "${vops.monitor.icmp-cron:0 * * * * ?}")
    public void collect() {
        List<Host> hosts = hostService.listAllEnabled();
        if (hosts == null || hosts.isEmpty()) return;
        log.debug("ICMP collect start, hosts={}", hosts.size());
        int poolSize = Math.min(hosts.size(), COLLECT_PARALLEL);
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        try {
            for (Host host : hosts) {
                Long hostId = host.getId();
                String hostname = host.getHostname();
                if (hostId == null || hostname == null || hostname.isBlank()) continue;
                executor.submit(() -> {
                    try {
                        collectOne(hostId, hostname.trim());
                    } catch (Exception e) {
                        log.debug("ICMP collect hostId={} hostname={}: {}", hostId, hostname, e.getMessage());
                    }
                });
            }
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(2, TimeUnit.MINUTES)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public void collectOne(Long hostId, String hostname) {
        Date now = new Date();
        Long rttMs = HostMetric.ICMP_UNREACHABLE_RTT_MS;
        int exitCode = -1;
        String output = "";
        try {
            List<String> cmd = pingCommand(hostname);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(PING_TIMEOUT_SEC, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                p.waitFor(2, TimeUnit.SECONDS);
                rttMs = HostMetric.ICMP_UNREACHABLE_RTT_MS;
            } else {
                output = readFully(p.getInputStream());
                exitCode = p.exitValue();
                if (exitCode == 0) {
                    Long parsed = parseRttMs(output);
                    if (parsed != null) {
                        rttMs = parsed;
                    } else {
                        rttMs = HostMetric.ICMP_UNREACHABLE_RTT_MS;
                    }
                } else {
                    rttMs = HostMetric.ICMP_UNREACHABLE_RTT_MS;
                }
            }
        } catch (Exception e) {
            log.warn("ICMP ping failed hostname={} hostId={}: {}", hostname, hostId, e.getMessage());
            if (log.isDebugEnabled()) log.debug("ICMP ping output hostname={}: {}", hostname, output);
            rttMs = HostMetric.ICMP_UNREACHABLE_RTT_MS;
        }
        if (rttMs >= HostMetric.ICMP_UNREACHABLE_RTT_MS && log.isDebugEnabled()) {
            log.debug("ICMP unreachable hostname={} hostId={} exitCode={} output={}", hostname, hostId, exitCode, output);
        }
        try {
            hostMetricService.saveOrUpdateIcmp(hostId, now, rttMs);
            Optional<HostMetric> latest = hostMetricService.getLatestByHostId(hostId);
            latest.ifPresent(m -> alertRuleEngine.evaluate(hostId, m));
        } catch (Exception e) {
            log.warn("ICMP save failed hostId={} hostname={} rttMs={}: {}", hostId, hostname, rttMs, e.getMessage());
        }
    }

    private static List<String> pingCommand(String hostname) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return List.of("ping", "-n", "1", "-w", String.valueOf(PING_TIMEOUT_SEC * 1000), hostname);
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return List.of("ping", "-c", "1", "-t", String.valueOf(PING_TIMEOUT_SEC), hostname);
        }
        return List.of("ping", "-c", "1", "-W", String.valueOf(PING_TIMEOUT_SEC), hostname);
    }

    private static String readFully(java.io.InputStream is) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
        } catch (Exception ignored) { }
        return sb.toString();
    }

    private static Long parseRttMs(String output) {
        if (output == null) return null;
        Matcher m = RTT_PATTERN.matcher(output);
        while (m.find()) {
            for (int i = 1; i <= m.groupCount(); i++) {
                String num = m.group(i);
                if (num == null || num.isEmpty()) continue;
                try {
                    double v = Double.parseDouble(num);
                    if (v >= 0) return (long) Math.round(v);
                } catch (NumberFormatException ignored) { }
            }
        }
        return null;
    }
}
