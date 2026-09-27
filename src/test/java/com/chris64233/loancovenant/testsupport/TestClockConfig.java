package com.chris64233.loancovenant.testsupport;

import java.time.Clock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** 用可变时钟替换系统时钟，便于有效期等时间相关测试。 */
@TestConfiguration
public class TestClockConfig {

    @Bean
    @Primary
    public Clock testClock() {
        return new MutableClock();
    }
}
