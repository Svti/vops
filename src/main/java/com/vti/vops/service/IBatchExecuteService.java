package com.vti.vops.service;

/**
 * 批量执行服务接口
 */
public interface IBatchExecuteService {

    /** 提交一次执行；scheduleId 可选，由定时或「立即执行」触发时传入 */
    Long submit(String name, String command, String hostIds, Long operatorId, Long scheduleId);
}
