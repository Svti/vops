package com.vti.vops.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.vti.vops.entity.AlertRule;

import java.util.List;

/**
 * 告警规则服务接口
 */
public interface IAlertRuleService extends IService<AlertRule> {

    List<AlertRule> listEnabledByHostId(Long hostId);
}
