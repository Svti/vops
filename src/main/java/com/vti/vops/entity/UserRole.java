package com.vti.vops.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

@Data
@TableName("sys_user_role")
public class UserRole {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long roleId;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
    @TableLogic
    private Integer deleted;
}
