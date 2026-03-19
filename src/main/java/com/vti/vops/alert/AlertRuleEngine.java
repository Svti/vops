package com.vti.vops.alert;

import com.vti.vops.entity.AlertEvent;
import com.vti.vops.entity.AlertRule;
import com.vti.vops.entity.HostMetric;
import com.vti.vops.mapper.AlertEventMapper;
import com.vti.vops.service.IAlertRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 告警规则引擎：根据当前指标与规则判断是否触发，写入告警事件并触发通知。
 * 支持持续时长（durationSeconds）：仅当条件连续满足达到指定秒数后才触发。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertRuleEngine {

    private final IAlertRuleService alertRuleService;
    private final AlertEventMapper alertEventMapper;
    private final AlertNotifierDispatcher notifierDispatcher;

    /** 持续时长判断：key = ruleId:hostId, value = 条件首次满足的时间戳(ms) */
    private final Map<String, Long> conditionFirstTrueAt = new ConcurrentHashMap<>();

    public void evaluate(Long hostId, HostMetric metric) {
        List<AlertRule> rules = alertRuleService.listEnabledByHostId(hostId);
        long nowMs = System.currentTimeMillis();
        for (AlertRule rule : rules) {
            Double value = getMetricValue(metric, rule.getMetricKey());
            if (value == null) {
                clearDurationState(rule.getId(), hostId);
                continue;
            }
            boolean conditionMet = evaluateOperator(value, rule.getOperator(), rule.getThreshold());
            int durationSec = rule.getDurationSeconds() != null ? rule.getDurationSeconds() : 0;
            if (!conditionMet) {
                clearDurationState(rule.getId(), hostId);
                continue;
            }
            boolean trigger;
            if (durationSec <= 0) {
                trigger = true;
            } else {
                String key = rule.getId() + ":" + hostId;
                conditionFirstTrueAt.putIfAbsent(key, nowMs);
                long firstMs = conditionFirstTrueAt.get(key);
                trigger = (nowMs - firstMs) >= durationSec * 1000L;
                if (trigger) conditionFirstTrueAt.remove(key);
            }
            if (trigger) {
                AlertEvent event = new AlertEvent();
                event.setRuleId(rule.getId());
                event.setHostId(hostId);
                event.setMetricKey(rule.getMetricKey());
                event.setCurrentValue(value);
                event.setThreshold(rule.getThreshold());
                String detail = "icmp_rtt_ms".equals(rule.getMetricKey()) && value != null && value >= HostMetric.ICMP_UNREACHABLE_RTT_MS
                        ? "ICMP 不可达：本机无法 ping 通该主机"
                        : String.format("%s 当前值 %.2f %s 阈值 %.2f", rule.getMetricKey(), value, rule.getOperator(), rule.getThreshold());
                String ruleName = rule.getName() != null && !rule.getName().isEmpty() ? rule.getName() : "告警规则";
                int severity = rule.getSeverity() != null ? rule.getSeverity() : 1;
                event.setSeverity(severity);
                String severityLabel = severityLabel(severity);
                event.setMessage(AlertMessageFormatter.formatMarkdown(severityLabel, ruleName, "{{hostName}}", detail));
                event.setStatus(1);
                alertEventMapper.insert(event);
                notifierDispatcher.dispatch(event);
            }
        }
    }

    private Double getMetricValue(HostMetric m, String key) {
        if (key == null) return null;
        switch (key) {
            case "cpu_usage": return m.getCpuUsage();
            case "mem_usage": return m.getMemUsagePercent();
            case "disk_usage": return m.getDiskUsagePercent() != null ? m.getDiskUsagePercent().doubleValue() : null;
            case "icmp_rtt_ms": return m.getIcmpRttMs() != null ? m.getIcmpRttMs().doubleValue() : null;
            default: return null;
        }
    }

    private boolean evaluateOperator(Double value, String op, Double threshold) {
        if (threshold == null) return false;
        String o = op != null ? op : "gte";
        switch (o) {
            case "gt": return value > threshold;
            case "gte": return value >= threshold;
            case "lt": return value < threshold;
            case "lte": return value <= threshold;
            case "eq": return Math.abs(value - threshold) < 0.0001;
            default: return value >= threshold;
        }
    }

    private void clearDurationState(Long ruleId, Long hostId) {
        if (ruleId != null && hostId != null) {
            conditionFirstTrueAt.remove(ruleId + ":" + hostId);
        }
    }

    private static String severityLabel(int severity) {
        if (severity == 3) return "严重";
        if (severity == 2) return "重要";
        return "一般";
    }
}
