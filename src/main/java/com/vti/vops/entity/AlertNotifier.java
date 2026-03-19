package com.vti.vops.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

/**
 * 告警通知渠道配置
 */
@Data
@TableName("alert_notifier")
public class AlertNotifier {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    /** dingtalk, feishu, wecom, email */
    private String channel;
    private String configJson;
    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
    @TableLogic
    private Integer deleted;
}
