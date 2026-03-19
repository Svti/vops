package com.vti.vops.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

@Data
@TableName("batch_task_log")
public class BatchTaskLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long hostId;
    private Integer exitCode;
    private String output;
    private String error;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
