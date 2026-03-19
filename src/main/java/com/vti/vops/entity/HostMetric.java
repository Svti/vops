package com.vti.vops.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

/**
 * 主机指标历史（MySQL 存储）
 */
@Data
@TableName("host_metric")
public class HostMetric {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long hostId;
    private Date collectTime;

    private Double cpuUsage;
    private Integer cpuCores;
    private String loadAvg;
    private Long uptimeSeconds;
    private Long memTotal;
    private Long memUsed;
    private Long memFree;
    private Double memUsagePercent;
    private Integer diskUsagePercent;
    private Long diskTotal;
    private Long diskUsed;

    private String diskJson;
    private Long networkRxRateBps;
    private Long networkTxRateBps;
    private String processSummary;
    private String processTopCpu;

    /** ICMP 往返时延（毫秒），不可达时存 9999，见 ICMP_UNREACHABLE_RTT_MS */
    private Long icmpRttMs;

    /** 约定：icmpRttMs 为该值时表示不可达，告警规则可用 >= 9999 或 = 9999 */
    public static final long ICMP_UNREACHABLE_RTT_MS = 9999L;

    /** 展示用：内存/磁盘容量友好格式，不持久化 */
    public String getMemUsedFormatted() { return formatBytes(memUsed); }
    public String getMemTotalFormatted() { return formatBytes(memTotal); }
    public String getDiskUsedFormatted() { return formatBytes(diskUsed); }
    public String getDiskTotalFormatted() { return formatBytes(diskTotal); }

    private static String formatBytes(Long bytes) {
        if (bytes == null || bytes <= 0) return "0";
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fK", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1fM", bytes / (1024.0 * 1024));
        if (bytes < 1024L * 1024 * 1024 * 1024) return String.format("%.1fG", bytes / (1024.0 * 1024 * 1024));
        return String.format("%.1fT", bytes / (1024.0 * 1024 * 1024 * 1024));
    }
}
