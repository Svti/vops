package com.vti.vops.monitor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 应用就绪后异步触发一次全量采集，使主机列表能尽快显示在线状态与 CPU/内存/磁盘信息。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonitorStartupRunner {

    private final MonitorCollector monitorCollector;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        Thread t = new Thread(() -> {
            try {
                log.info("Startup: triggering initial host metrics collection");
                monitorCollector.collect();
                log.info("Startup: initial host metrics collection done");
            } catch (Exception e) {
                log.warn("Startup: initial metrics collection failed: {}", e.getMessage());
            }
        }, "monitor-startup-collect");
        t.setDaemon(false);
        t.start();
    }
}
