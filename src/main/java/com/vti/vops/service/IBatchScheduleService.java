package com.vti.vops.service;

import com.vti.vops.entity.BatchSchedule;

import java.util.List;

/**
 * 批量执行定时任务服务
 */
public interface IBatchScheduleService {

    List<BatchSchedule> list();

    BatchSchedule getById(Long id);

    void save(BatchSchedule schedule, Long operatorId);

    void removeById(Long id);

    /** 立即执行一次该定时任务（生成一次执行记录） */
    Long runNow(Long scheduleId, Long operatorId);

    /** 由调度器调用：检查并执行到期的定时任务 */
    void triggerDueSchedules();

    /** 启停切换：enabled 1↔0 */
    void toggle(Long scheduleId);

    /** 根据 Cron 表达式计算未来 N 次执行时间（时间戳毫秒），无效或空 cron 返回空列表 */
    List<Long> getNextScheduleTimes(Long scheduleId, int count);
}
