package com.vti.vops.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.vti.vops.entity.AlertRule;
import com.vti.vops.mapper.AlertRuleMapper;
import com.vti.vops.service.IAlertRuleService;
import com.vti.vops.service.IHostAlertRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AlertRuleServiceImpl extends ServiceImpl<AlertRuleMapper, AlertRule> implements IAlertRuleService {

    private final IHostAlertRuleService hostAlertRuleService;

    @Override
    public List<AlertRule> listEnabledByHostId(Long hostId) {
        if (hostId == null) return List.of();
        List<Long> ruleIds = hostAlertRuleService.listRuleIdsByHostId(hostId);
        if (ruleIds.isEmpty()) return List.of();
        return list(new LambdaQueryWrapper<AlertRule>()
                .in(AlertRule::getId, ruleIds)
                .eq(AlertRule::getEnabled, 1));
    }
}
