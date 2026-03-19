package com.vti.vops.config;

import com.vti.vops.util.EncryptUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EncryptConfig {

    @Bean
    public EncryptUtil encryptUtil(@Value("${vops.encrypt.key:vops-aes-key-32bytes!!!!}") String key) {
        return new EncryptUtil(key);
    }
}
