package com.vti.vops.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

/**
 * 操作审计日志
 */
@Data
@TableName("audit_log")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private String action;
    private String resourceType;
    private String resourceId;
    private String detail;
    private String ip;
    private String userAgent;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
