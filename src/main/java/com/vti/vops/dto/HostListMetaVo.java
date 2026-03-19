package com.vti.vops.dto;

import com.vti.vops.entity.AlertRule;
import com.vti.vops.entity.HostGroup;
import com.vti.vops.entity.SshKey;
import lombok.Data;

import java.util.List;

/**
 * 主机列表页公共元数据（分组、告警规则、SSH 密钥列表），全局缓存。
 */
@Data
public class HostListMetaVo {
    private List<HostGroup> groups;
    private List<AlertRule> alertRules;
    private List<SshKey> sshKeys;
}
