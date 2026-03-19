package com.vti.vops.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 主机列表用：在线状态及最新 CPU/内存/磁盘指标
 */
@Data
@Builder
public class HostStatusVo {

    private boolean online;
    /** CPU 使用率 0–100 */
    private Double cpuUsage;
    /** CPU 核数 */
    private Integer cpuCores;
    /** 系统平均负载：1/5/15 分钟，如 "1.2 0.8 0.5" */
    private String loadAvg;
    /** 内存使用率 0–100 */
    private Double memUsagePercent;
    private Long memUsed;
    private Long memTotal;
    /** 内存已用/总量友好显示，如 2.1G / 8.0G */
    private String memUsedFormatted;
    private String memTotalFormatted;
    /** 根分区磁盘使用率 0–100 */
    private Integer diskUsagePercent;
    /** 根分区磁盘已用友好显示，如 20G */
    private String diskUsedFormatted;
    /** 根分区磁盘总容量友好显示，如 500G */
    private String diskTotalFormatted;
}
