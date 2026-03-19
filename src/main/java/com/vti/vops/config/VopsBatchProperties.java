package com.vti.vops.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 批量执行线程池配置，前缀 vops.batch。
 */
@Data
@ConfigurationProperties(prefix = "vops.batch")
public class VopsBatchProperties {

    private int corePoolSize = 4;
    private int maxPoolSize = 20;
    private int queueCapacity = 100;
}
