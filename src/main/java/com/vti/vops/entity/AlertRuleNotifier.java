package com.vti.vops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 告警规则与通知渠道关联：在告警规则中配置该规则触发时使用的渠道
 */
@Data
@TableName("alert_rule_notifier")
public class AlertRuleNotifier {

    private Long ruleId;
    private Long notifierId;
}
