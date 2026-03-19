package com.vti.vops.batch;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vti.vops.entity.BatchTask;
import com.vti.vops.entity.BatchTaskLog;
import com.vti.vops.mapper.BatchTaskLogMapper;
import com.vti.vops.mapper.BatchTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 定时清理批量执行记录与运行日志，仅保留最近 N 天，每日低峰执行。
 * 先删 batch_task_log（按过期任务的 id），再删 batch_task。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchCleanupTask {

    private final BatchTaskMapper batchTaskMapper;
    private final BatchTaskLogMapper batchTaskLogMapper;

    /** 保留最近天数，默认 7 天。可配置 vops.batch.retention-days */
    @Value("${vops.batch.retention-days:7}")
    private int retentionDays;

    /**
     * 每日低峰时间清理过期执行记录与日志。默认 02:00 执行，可配置 vops.batch.cleanup-cron（cron 表达式）。
     */
    @Scheduled(cron = "${vops.batch.cleanup-cron:0 0 2 * * ?}")
    public void cleanup() {
        if (retentionDays <= 0) {
            log.debug("Batch cleanup skipped: retention-days={}", retentionDays);
            return;
        }
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -retentionDays);
        Date before = cal.getTime();

        List<Long> oldTaskIds = batchTaskMapper.selectList(
                        new LambdaQueryWrapper<BatchTask>()
                                .lt(BatchTask::getCreateTime, before)
                                .select(BatchTask::getId))
                .stream()
                .map(BatchTask::getId)
                .collect(Collectors.toList());

        if (oldTaskIds.isEmpty()) {
            return;
        }

        int deletedLogs = batchTaskLogMapper.delete(
                new LambdaQueryWrapper<BatchTaskLog>().in(BatchTaskLog::getTaskId, oldTaskIds));
        int deletedTasks = batchTaskMapper.delete(new LambdaQueryWrapper<BatchTask>().lt(BatchTask::getCreateTime, before));

        log.info("Batch cleanup: deleted {} task records and {} log records older than {} days",
                deletedTasks, deletedLogs, retentionDays);
    }
}
