package com.chris64233.loancovenant.service;

import java.math.BigDecimal;

/**
 * 单项契约判断结果，同时作为批准判断依据的组成部分。
 *
 * @param metricKey   指标键
 * @param displayName 契约名称
 * @param operator    比较方向
 * @param threshold   阈值
 * @param actualValue 快照实际值（缺失时为 null）
 * @param satisfied   是否满足
 * @param detail      人类可读说明
 */
public record CovenantCheck(
        String metricKey,
        String displayName,
        String operator,
        BigDecimal threshold,
        BigDecimal actualValue,
        boolean satisfied,
        String detail) {
}
