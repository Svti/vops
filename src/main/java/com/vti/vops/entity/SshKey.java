package com.vti.vops.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

/**
 * SSH 私钥（统一管理，添加主机时可选择）
 */
@Data
@TableName("ssh_key")
public class SshKey {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    /** 私钥内容密文（AES） */
    private String content;
    private String description;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
    @TableLogic
    private Integer deleted;
}
