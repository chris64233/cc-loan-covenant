package com.chris64233.loancovenant.api.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 还款请求：金额必须为正且不能超过未还本金。 */
public record RepaymentRequest(@NotNull @Positive BigDecimal amount) {
}
