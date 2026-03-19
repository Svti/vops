package com.vti.vops.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vti.vops.entity.HostAlertRule;
import com.vti.vops.mapper.HostAlertRuleMapper;
import com.vti.vops.service.IHostAlertRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HostAlertRuleServiceImpl implements IHostAlertRuleService {

    private final HostAlertRuleMapper hostAlertRuleMapper;

    @Override
    public List<Long> listRuleIdsByHostId(Long hostId) {
        if (hostId == null) return List.of();
        return hostAlertRuleMapper.selectList(
                new LambdaQueryWrapper<HostAlertRule>().eq(HostAlertRule::getHostId, hostId))
                .stream()
                .map(HostAlertRule::getRuleId)
                .collect(Collectors.toList());
    }

    @Override
    public Map<Long, Integer> listRuleCountByHostIds(List<Long> hostIds) {
        if (hostIds == null || hostIds.isEmpty()) return Map.of();
        List<HostAlertRule> list = hostAlertRuleMapper.selectList(
                new LambdaQueryWrapper<HostAlertRule>().in(HostAlertRule::getHostId, hostIds));
        Map<Long, Integer> map = new HashMap<>();
        for (HostAlertRule har : list) {
            if (har.getHostId() != null) {
                map.merge(har.getHostId(), 1, Integer::sum);
            }
        }
        for (Long hid : hostIds) {
            map.putIfAbsent(hid, 0);
        }
        return map;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setRulesForHost(Long hostId, List<Long> ruleIds) {
        if (hostId == null) return;
        hostAlertRuleMapper.delete(new LambdaQueryWrapper<HostAlertRule>().eq(HostAlertRule::getHostId, hostId));
        if (ruleIds != null && !ruleIds.isEmpty()) {
            for (Long ruleId : ruleIds) {
                if (ruleId == null) continue;
                HostAlertRule har = new HostAlertRule();
                har.setHostId(hostId);
                har.setRuleId(ruleId);
                hostAlertRuleMapper.insert(har);
            }
        }
    }

    @Override
    public List<Long> listHostIdsByRuleId(Long ruleId) {
        if (ruleId == null) return List.of();
        return hostAlertRuleMapper.selectList(
                new LambdaQueryWrapper<HostAlertRule>().eq(HostAlertRule::getRuleId, ruleId))
                .stream()
                .map(HostAlertRule::getHostId)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByRuleId(Long ruleId) {
        if (ruleId == null) return;
        hostAlertRuleMapper.delete(new LambdaQueryWrapper<HostAlertRule>().eq(HostAlertRule::getRuleId, ruleId));
    }
}
