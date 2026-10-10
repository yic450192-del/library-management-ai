package com.example.library.domain.shared;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 金额值对象。用 BigDecimal 避免浮点误差。
 * 值对象：无标识，属性相等即相等，不可变。
 */
public record Money(BigDecimal amount, String currency) {

    public Money {
        Objects.requireNonNull(amount, "amount 不能为空");
        currency = (currency == null || currency.isBlank()) ? "CNY" : currency;
    }

    public static Money cny(String value) {
        return new Money(new BigDecimal(value), "CNY");
    }

    public static Money zero() {
        return new Money(BigDecimal.ZERO, "CNY");
    }

    public Money multiply(long times) {
        return new Money(amount.multiply(BigDecimal.valueOf(times)), currency);
    }

    /** 取较小者，用于罚金上限截断（BR-009） */
    public Money cap(Money max) {
        return amount.compareTo(max.amount()) <= 0 ? this : max;
    }

    public boolean isPositive() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }
}
