package com.vti.vops.alert;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vti.vops.entity.AlertEvent;
import com.vti.vops.entity.AlertNotifier;
import com.vti.vops.entity.AlertRuleNotifier;
import com.vti.vops.mapper.AlertNotifierMapper;
import com.vti.vops.mapper.AlertRuleNotifierMapper;
import com.vti.vops.mapper.HostMapper;
import com.vti.vops.entity.Host;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 告警通知分发：查询启用的通知渠道，调用各 notifier 插件发送
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertNotifierDispatcher {

    private static final String TEST_HOST = "测试";
    private static final String TEST_MESSAGE = "这是一条测试消息，用于验证渠道配置是否正确。";

    private final AlertNotifierMapper alertNotifierMapper;
    private final AlertRuleNotifierMapper alertRuleNotifierMapper;
    private final HostMapper hostMapper;
    private final List<AlertNotifierPlugin> plugins;

    /** 向指定渠道发送测试消息，返回 success 与可选的 message（失败时） */
    public Map<String, Object> sendTest(AlertNotifier notifier) {
        if (notifier == null || notifier.getChannel() == null) {
            return Map.of("success", false, "message", "渠道未配置");
        }
        for (AlertNotifierPlugin plugin : plugins) {
            if (plugin.supports(notifier.getChannel())) {
                try {
                    plugin.send(notifier, TEST_HOST, TEST_MESSAGE, 0);
                    return Map.of("success", true);
                } catch (Exception e) {
                    log.warn("Test send failed for {}: {}", notifier.getChannel(), e.getMessage());
                    return Map.of("success", false, "message", e.getMessage() != null ? e.getMessage() : "发送失败");
                }
            }
        }
        return Map.of("success", false, "message", "不支持的渠道类型: " + notifier.getChannel());
    }

    /** 仅向该告警规则配置的通知渠道发送（规则在告警规则编辑中勾选）；未配置渠道则不发送。 */
    public void dispatch(AlertEvent event) {
        Long ruleId = event.getRuleId();
        if (ruleId == null) return;
        List<Long> notifierIds = alertRuleNotifierMapper.selectList(
                new LambdaQueryWrapper<AlertRuleNotifier>().eq(AlertRuleNotifier::getRuleId, ruleId))
                .stream().map(AlertRuleNotifier::getNotifierId).filter(id -> id != null).distinct().collect(Collectors.toList());
        if (notifierIds.isEmpty()) return;
        List<AlertNotifier> notifiers = alertNotifierMapper.selectList(
                new LambdaQueryWrapper<AlertNotifier>()
                        .in(AlertNotifier::getId, notifierIds)
                        .eq(AlertNotifier::getEnabled, 1));
        if (notifiers.isEmpty()) return;
        Host host = event.getHostId() != null ? hostMapper.selectById(event.getHostId()) : null;
        String hostName = host != null ? host.getName() : "unknown";
        String message = event.getMessage() != null ? event.getMessage().replace("{{hostName}}", hostName) : "";
        for (AlertNotifier n : notifiers) {
            for (AlertNotifierPlugin plugin : plugins) {
                if (plugin.supports(n.getChannel())) {
                    try {
                        plugin.send(n, hostName, message, event.getSeverity() != null ? event.getSeverity() : 1);
                    } catch (Exception e) {
                        log.warn("Notifier {} failed: {}", n.getChannel(), e.getMessage());
                    }
                    break;
                }
            }
        }
    }
}
