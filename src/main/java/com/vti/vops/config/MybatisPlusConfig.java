package com.vti.vops.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Date;
import java.util.Map;

/**
 * MyBatis-Plus 配置：拦截器、自动填充、以及供启动时 setDefaultProperties 使用的默认属性。
 */
@Configuration
public class MybatisPlusConfig {

    /** 极少修改的 mybatis-plus 默认属性，供 VopsApplication 合并到默认配置。 */
    public static Map<String, Object> defaultProperties() {
        return Map.ofEntries(
                Map.entry("mybatis-plus.type-aliases-package", "com.vti.vops.entity"),
                Map.entry("mybatis-plus.configuration.map-underscore-to-camel-case", true),
                Map.entry("mybatis-plus.global-config.db-config.id-type", "auto"),
                Map.entry("mybatis-plus.global-config.db-config.logic-delete-field", "deleted"),
                Map.entry("mybatis-plus.global-config.db-config.logic-delete-value", 1),
                Map.entry("mybatis-plus.global-config.db-config.logic-not-delete-value", 0)
        );
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                this.strictInsertFill(metaObject, "createTime", Date.class, new Date());
                this.strictInsertFill(metaObject, "updateTime", Date.class, new Date());
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updateTime", Date.class, new Date());
            }
        };
    }
}
