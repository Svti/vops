package com.vti.vops.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.vti.vops.entity.BatchSchedule;
import com.vti.vops.mapper.BatchScheduleMapper;
import com.vti.vops.service.IBatchExecuteService;
import com.vti.vops.service.IBatchScheduleService;
import com.vti.vops.service.IHostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchScheduleServiceImpl implements IBatchScheduleService {

    private final BatchScheduleMapper scheduleMapper;
    private final IBatchExecuteService batchExecuteService;
    private final IHostService hostService;

    @Override
    public List<BatchSchedule> list() {
        return scheduleMapper.selectList(
                new LambdaQueryWrapper<BatchSchedule>().orderByAsc(BatchSchedule::getId));
    }

    @Override
    public BatchSchedule getById(Long id) {
        return scheduleMapper.selectById(id);
    }

    @Override
    public void save(BatchSchedule schedule, Long operatorId) {
        if (schedule.getEnabled() == null) schedule.setEnabled(1);
        if (schedule.getId() != null) {
            // 使用 UpdateWrapper 显式 set 各字段，否则 MyBatis-Plus 对 null 不更新，无法清空 host_ids/group_ids
            LambdaUpdateWrapper<BatchSchedule> w = new LambdaUpdateWrapper<BatchSchedule>()
                    .eq(BatchSchedule::getId, schedule.getId())
                    .set(BatchSchedule::getName, schedule.getName())
                    .set(BatchSchedule::getCommand, schedule.getCommand())
                    .set(BatchSchedule::getHostIds, schedule.getHostIds())
                    .set(BatchSchedule::getGroupIds, schedule.getGroupIds())
                    .set(BatchSchedule::getCronExpression, schedule.getCronExpression())
                    .set(BatchSchedule::getEnabled, schedule.getEnabled())
                    .set(BatchSchedule::getUpdateTime, new Date());
            scheduleMapper.update(null, w);
        } else {
            schedule.setOperatorId(operatorId);
            scheduleMapper.insert(schedule);
        }
    }

    @Override
    public void removeById(Long id) {
        scheduleMapper.deleteById(id);
    }

    @Override
    public Long runNow(Long scheduleId, Long operatorId) {
        BatchSchedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null) return null;
        String hostIdsStr = schedule.getHostIds();
        if (hostIdsStr == null || hostIdsStr.isBlank()) {
            String groupIdsStr = schedule.getGroupIds();
            if (groupIdsStr == null || groupIdsStr.isBlank()) return null;
            List<Long> groupIds = Arrays.stream(groupIdsStr.trim().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
            if (groupIds.isEmpty()) return null;
            List<Long> hostIds = hostService.listHostIdsByGroupIds(groupIds);
            if (hostIds.isEmpty()) return null;
            hostIdsStr = hostIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        }
        return batchExecuteService.submit(
                schedule.getName(),
                schedule.getCommand(),
                hostIdsStr,
                operatorId != null ? operatorId : schedule.getOperatorId(),
                schedule.getId());
    }

    @Override
    public void triggerDueSchedules() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.minus(1, ChronoUnit.MINUTES);
        for (BatchSchedule schedule : list()) {
            if (schedule.getEnabled() == null || schedule.getEnabled() != 1) continue;
            String cron = schedule.getCronExpression();
            if (cron == null || cron.isBlank()) continue;
            try {
                CronExpression expression = CronExpression.parse(cron);
                LocalDateTime next = expression.next(windowStart);
                if (next != null && !next.isAfter(now)) {
                    runNow(schedule.getId(), schedule.getOperatorId());
                    log.info("Batch schedule triggered: id={}, name={}", schedule.getId(), schedule.getName());
                }
            } catch (Exception e) {
                log.warn("Invalid cron or error triggering schedule id={}: {}", schedule.getId(), e.getMessage());
            }
        }
    }

    @Override
    public void toggle(Long scheduleId) {
        BatchSchedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null) return;
        schedule.setEnabled(schedule.getEnabled() != null && schedule.getEnabled() == 1 ? 0 : 1);
        scheduleMapper.updateById(schedule);
    }

    @Override
    public List<Long> getNextScheduleTimes(Long scheduleId, int count) {
        if (scheduleId == null || count <= 0) return List.of();
        BatchSchedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null) return List.of();
        String cron = schedule.getCronExpression();
        if (cron == null || cron.isBlank()) return List.of();
        try {
            CronExpression expression = CronExpression.parse(cron);
            List<Long> times = new ArrayList<>(count);
            LocalDateTime cursor = LocalDateTime.now();
            for (int i = 0; i < count; i++) {
                cursor = expression.next(cursor);
                if (cursor == null) break;
                times.add(cursor.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
                cursor = cursor.plusSeconds(1);
            }
            return times;
        } catch (Exception e) {
            log.debug("Invalid cron expression for schedule id={}: {}", scheduleId, e.getMessage());
            return List.of();
        }
    }
}
