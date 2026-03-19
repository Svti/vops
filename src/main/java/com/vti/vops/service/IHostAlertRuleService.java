package com.vti.vops.service;

import java.util.List;
import java.util.Map;

/**
 * 主机与告警规则关联：在主机管理中配置该主机适用的规则
 */
public interface IHostAlertRuleService {

    List<Long> listRuleIdsByHostId(Long hostId);

    /** 批量查询各主机的告警规则数量，用于列表缓存 */
    Map<Long, Integer> listRuleCountByHostIds(List<Long> hostIds);

    void setRulesForHost(Long hostId, List<Long> ruleIds);

    List<Long> listHostIdsByRuleId(Long ruleId);

    void deleteByRuleId(Long ruleId);
}
