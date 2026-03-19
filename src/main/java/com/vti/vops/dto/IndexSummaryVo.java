package com.vti.vops.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 首页运维总览数据（可缓存）
 */
@Data
public class IndexSummaryVo {
    private int totalHosts;
    private int onlineHosts;
    private double avgCpu;
    private double avgMem;
    private long totalAlertRules;
    private long totalBatchTasks;
    private List<Map<String, Object>> topHostsByCpu;
    private List<Map<String, Object>> topHostsByMem;
}
