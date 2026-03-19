package com.vti.vops.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

/**
 * 主机
 */
@Data
@TableName("host")
public class Host {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long groupId;
    private String name;
    private String hostname;
    private Integer port;
    private String username;
    /** SSH 密码或私钥密文（AES）；使用私钥库时可为空 */
    private String credential;
    /** 关联私钥库 ID，非空时连接使用私钥库内容 */
    private Long sshKeyId;
    /** password / privateKey */
    private String authType;
    private String description;
    private Integer status;
    /** 最近一次采集时间，用于判断在线/离线 */
    private Date lastMetricTime;

    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
    @TableLogic
    private Integer deleted;
}
