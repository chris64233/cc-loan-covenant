package com.chris64233.loancovenant.testsupport;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** 测试用可变时钟：默认固定在 2026-06-30，可按需推进或重置。 */
public class MutableClock extends Clock {

    private volatile Instant instant = Instant.parse("2026-06-30T10:00:00Z");
    private final ZoneId zone = ZoneOffset.UTC;

    public void setInstant(Instant instant) {
        this.instant = instant;
    }

    public void advance(Duration duration) {
        this.instant = this.instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
