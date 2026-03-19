package com.vti.vops.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.vti.vops.entity.BatchTaskLog;

import java.util.List;

/**
 * 批量任务日志服务接口
 */
public interface IBatchTaskLogService extends IService<BatchTaskLog> {

    List<BatchTaskLog> listByTaskId(Long taskId);
}
