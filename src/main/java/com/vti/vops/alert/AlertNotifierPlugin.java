package com.vti.vops.alert;

import com.vti.vops.entity.AlertNotifier;

/**
 * 告警通知插件接口
 */
public interface AlertNotifierPlugin {

    /** 是否支持该渠道类型 */
    boolean supports(String channel);

    /** 发送告警 */
    void send(AlertNotifier notifier, String hostName, String message, int severity);
}
