package com.vti.vops.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.vti.vops.entity.BatchTaskLog;
import com.vti.vops.mapper.BatchTaskLogMapper;
import com.vti.vops.service.IBatchTaskLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BatchTaskLogServiceImpl extends ServiceImpl<BatchTaskLogMapper, BatchTaskLog> implements IBatchTaskLogService {

    @Override
    public List<BatchTaskLog> listByTaskId(Long taskId) {
        return list(new LambdaQueryWrapper<BatchTaskLog>()
                .eq(BatchTaskLog::getTaskId, taskId)
                .orderByAsc(BatchTaskLog::getId));
    }
}
