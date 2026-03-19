package com.vti.vops.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

/**
 * 告警事件
 */
@Data
@TableName("alert_event")
public class AlertEvent {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ruleId;
    private Long hostId;
    private String metricKey;
    private Double currentValue;
    private Double threshold;
    private String message;
    private Integer severity;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
    private Date resolveTime;
}
