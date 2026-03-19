package com.vti.vops.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

/**
 * 告警规则
 */
@Data
@TableName("alert_rule")
public class AlertRule {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    /** cpu_usage, mem_usage, disk_usage, ... */
    private String metricKey;
    /** gt, gte, lt, lte, eq */
    private String operator;
    private Double threshold;
    private Integer durationSeconds;
    private Integer severity;
    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
    @TableLogic
    private Integer deleted;
}
