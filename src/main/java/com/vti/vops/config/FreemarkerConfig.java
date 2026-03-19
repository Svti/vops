package com.vti.vops.config;

import java.util.Map;

/**
 * FreeMarker 默认配置，供启动时 setDefaultProperties 使用。 实际绑定由 Spring Boot 的
 * spring.freemarker.* 完成。
 */
public final class FreemarkerConfig {

	private FreemarkerConfig() {
	}

	public static Map<String, Object> defaults() {
		return Map.of("spring.freemarker.template-loader-path", "classpath:/templates", 
				"spring.freemarker.suffix",".html", 
				"spring.freemarker.charset", "UTF-8", 
				"spring.freemarker.settings.output_encoding", "UTF-8",
				"spring.freemarker.cache", false);
	}
}
