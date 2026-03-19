package com.vti.vops.config;

import java.util.concurrent.TimeUnit;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * JVM 缓存配置：首页总览等可缓存数据使用 Caffeine，减少重复查询。
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** 首页总览 / 主机列表缓存过期时间（秒），过期后首次访问会重新计算 */
    private static final int CACHE_TTL_SECONDS = 60;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("indexSummary", "hostListData", "hostListMeta");
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(CACHE_TTL_SECONDS, TimeUnit.SECONDS)
                .maximumSize(500));
        return manager;
    }
}
