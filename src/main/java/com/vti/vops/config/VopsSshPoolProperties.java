package com.vti.vops.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SSH 连接池配置，前缀 vops.ssh.pool。
 */
@Data
@ConfigurationProperties(prefix = "vops.ssh.pool")
public class VopsSshPoolProperties {

    private int maxTotal = 100;
    private int maxIdle = 10;
    private int minIdle = 2;
    private int maxWaitMs = 5000;
    private long heartbeatIntervalMs = 1000;
    private long evictIdleMs = 60000;
}
