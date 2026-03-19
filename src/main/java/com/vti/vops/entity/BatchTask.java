package com.vti.vops.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

/**
 * 批量执行任务
 */
@Data
@TableName("batch_task")
public class BatchTask {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 来源定时任务 ID，空表示手动执行 */
    private Long scheduleId;
    private String name;
    private String command;
    private String hostIds;
    private Long operatorId;
    private Integer status;
    private Integer totalCount;
    private Integer successCount;
    private Integer failCount;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
    private Date finishTime;
}
