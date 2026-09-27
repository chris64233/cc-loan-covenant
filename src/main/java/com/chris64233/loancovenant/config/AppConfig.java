package com.chris64233.loancovenant.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    /** 统一时钟，测试可替换为固定时钟以验证有效期。 */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
