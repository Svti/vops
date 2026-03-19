package com.vti.vops.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 启用 vops 各模块的 @ConfigurationProperties。
 */
@Configuration
@EnableConfigurationProperties({
        VopsSshPoolProperties.class,
        VopsBatchProperties.class
})
public class VopsPropertiesConfig {
}
