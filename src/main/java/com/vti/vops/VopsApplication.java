package com.vti.vops;

import com.vti.vops.config.FreemarkerConfig;
import com.vti.vops.config.MybatisPlusConfig;
import com.vti.vops.config.ShiroConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.HashMap;
import java.util.Map;

/**
 * JSch OPS - 企业级 Linux 集群运维平台
 * 极少修改的配置默认值在此设置，application.properties 仅保留按环境常改的项（数据源、邮件、OIDC、加密密钥等）。
 * Freemarker / MyBatis-Plus / Shiro / SSH / Monitor / Batch 的默认值在各自配置类中定义。
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
@MapperScan("com.vti.vops.mapper")
public class VopsApplication {

    private static Map<String, Object> buildDefaults() {
        Map<String, Object> m = new HashMap<>(FreemarkerConfig.defaults());
        m.putAll(MybatisPlusConfig.defaultProperties());
        m.putAll(ShiroConfig.defaultProperties());
        m.putAll(Map.of(
                "spring.application.name", "vops",
                "vops.alert.notifier-timeout-ms", 5000,
                "vops.encrypt.key", "vops-aes-key-32bytes!!!!"
        ));
        return m;
    }

    public static void main(String[] args) {
        var app = new SpringApplication(VopsApplication.class);
        app.setDefaultProperties(buildDefaults());
        app.run(args);
    }
}
