package com.vti.vops.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

/**
 * 批量执行定时任务
 */
@Data
@TableName("batch_schedule")
public class BatchSchedule {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String command;
    private String hostIds;
    /** 按分组时保存的分组 ID 列表（逗号分隔），回显时用于显示「按分组」并勾选对应分组 */
    private String groupIds;
    private String cronExpression;
    private Integer enabled;

    private Long operatorId;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
    @TableLogic
    private Integer deleted;
}
