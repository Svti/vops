package com.vti.vops.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vti.vops.config.VopsBatchProperties;
import com.vti.vops.entity.BatchTask;
import com.vti.vops.entity.BatchTaskLog;
import com.vti.vops.mapper.BatchTaskLogMapper;
import com.vti.vops.mapper.BatchTaskMapper;
import com.vti.vops.service.IBatchExecuteService;
import com.vti.vops.ssh.SshClient;
import com.vti.vops.ssh.SshConnectionPool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class BatchExecuteServiceImpl implements IBatchExecuteService {

    private final SshConnectionPool sshPool;
    private final BatchTaskMapper taskMapper;
    private final BatchTaskLogMapper logMapper;
    private final ThreadPoolTaskExecutor batchExecutor;

    public BatchExecuteServiceImpl(
            SshConnectionPool sshPool,
            BatchTaskMapper taskMapper,
            BatchTaskLogMapper logMapper,
            VopsBatchProperties batchProps
    ) {
        this.sshPool = sshPool;
        this.taskMapper = taskMapper;
        this.logMapper = logMapper;
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(batchProps.getCorePoolSize());
        executor.setMaxPoolSize(batchProps.getMaxPoolSize());
        executor.setQueueCapacity(batchProps.getQueueCapacity());
        executor.setThreadNamePrefix("batch-");
        executor.initialize();
        this.batchExecutor = executor;
    }

    @Override
    public Long submit(String name, String command, String hostIds, Long operatorId, Long scheduleId) {
        List<Long> ids = Arrays.stream(hostIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .toList();
        BatchTask task = new BatchTask();
        task.setScheduleId(scheduleId);
        task.setName(name);
        task.setCommand(command);
        task.setHostIds(hostIds);
        task.setOperatorId(operatorId);
        task.setStatus(0);
        task.setTotalCount(ids.size());
        task.setSuccessCount(0);
        task.setFailCount(0);
        taskMapper.insert(task);
        Long taskId = task.getId();
        for (Long hostId : ids) {
            batchExecutor.execute(() -> runOne(taskId, hostId, command, ids.size()));
        }
        return taskId;
    }

    private void runOne(Long taskId, Long hostId, String command, int totalCount) {
        BatchTaskLog logEntry = new BatchTaskLog();
        logEntry.setTaskId(taskId);
        logEntry.setHostId(hostId);
        try {
            java.util.Optional<SshClient> clientOpt = sshPool.getClient(hostId);
            if (clientOpt.isEmpty()) {
                logEntry.setExitCode(-1);
                logEntry.setError("SSH connect failed");
                logMapper.insert(logEntry);
                tryFinish(taskId, totalCount);
                return;
            }
            SshClient client = clientOpt.get();
            try {
                String output = client.exec(command, 300);
                logEntry.setExitCode(0);
                logEntry.setOutput(output);
                logMapper.insert(logEntry);
            } finally {
                sshPool.release(hostId);
            }
        } catch (Exception e) {
            logEntry.setExitCode(-1);
            logEntry.setError(e.getMessage());
            logMapper.insert(logEntry);
        }
        tryFinish(taskId, totalCount);
    }

    private void tryFinish(Long taskId, int totalCount) {
        long count = logMapper.selectCount(new LambdaQueryWrapper<BatchTaskLog>().eq(BatchTaskLog::getTaskId, taskId));
        if (count < totalCount) return;
        BatchTask task = taskMapper.selectById(taskId);
        if (task == null) return;
        long success = logMapper.selectCount(new LambdaQueryWrapper<BatchTaskLog>().eq(BatchTaskLog::getTaskId, taskId).eq(BatchTaskLog::getExitCode, 0));
        task.setStatus(1);
        task.setSuccessCount((int) success);
        task.setFailCount((int) (totalCount - success));
        task.setFinishTime(new java.util.Date());
        taskMapper.updateById(task);
    }
}
