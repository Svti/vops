package com.vti.vops.batch;

import com.vti.vops.service.IBatchScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每分钟检查并执行到期的批量定时任务
 */
@Component
@RequiredArgsConstructor
public class BatchScheduleRunner {

    private final IBatchScheduleService batchScheduleService;

    @Scheduled(cron = "0 * * * * ?")
    public void runDueSchedules() {
        batchScheduleService.triggerDueSchedules();
    }
}
