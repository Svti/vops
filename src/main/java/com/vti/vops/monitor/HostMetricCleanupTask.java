package com.vti.vops.monitor;

import com.vti.vops.service.IHostMetricService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.Date;

/**
 * 定时清理主机指标历史数据，仅保留最近 N 天，每日低峰执行。
 * DELETE 后空间在表内可复用；若需回收物理磁盘空间，可开启 cleanup-optimize-table 在清理后执行 OPTIMIZE TABLE。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HostMetricCleanupTask {

    private final IHostMetricService hostMetricService;
    private final JdbcTemplate jdbcTemplate;

    /** 保留最近天数，默认 3 天。可配置 vops.monitor.metric-retention-days */
    @Value("${vops.monitor.metric-retention-days:3}")
    private int retentionDays;

    /**
     * 每日低峰时间清理过期指标记录。默认 03:00 执行，可配置 vops.monitor.cleanup-cron（cron 表达式）。
     */
    @Scheduled(cron = "${vops.monitor.cleanup-cron:0 0 3 * * ?}")
    public void cleanup() {
        if (retentionDays <= 0) {
            log.debug("Metric cleanup skipped: retention-days={}", retentionDays);
            return;
        }
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -retentionDays);
        Date before = cal.getTime();
        int deleted = hostMetricService.deleteOlderThan(before);
        if (deleted > 0) {
            log.info("Host metric cleanup: deleted {} records older than {} days", deleted, retentionDays);
            
            try {
                jdbcTemplate.execute("OPTIMIZE TABLE host_metric");
                log.info("Host metric cleanup: OPTIMIZE TABLE host_metric completed");
            } catch (Exception e) {
                log.warn("Host metric cleanup: OPTIMIZE TABLE failed: {}", e.getMessage());
            }
        }
    }
}
