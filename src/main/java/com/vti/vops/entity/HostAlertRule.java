package com.vti.vops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 主机与告警规则关联：在主机管理中为每台主机配置适用的规则
 */
@Data
@TableName("host_alert_rule")
public class HostAlertRule {

    private Long hostId;
    private Long ruleId;
}
