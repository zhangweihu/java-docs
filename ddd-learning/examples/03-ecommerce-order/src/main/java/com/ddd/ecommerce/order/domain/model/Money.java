package com.ddd.ecommerce.order.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 货币值对象（Java 17 record）。
 *
 * <p>不可变 + 全字段 equals + 无 setter。
 */
public record Money(BigDecimal amount, Currency currency) {

    public static final Money ZERO = new Money(BigDecimal.ZERO, Currency.CNY);

    public Money {
        Objects.requireNonNull(amount, "金额不能为空");
        Objects.requireNonNull(currency, "币种不能为空");
        if (amount.scale() > 2) {
            throw new IllegalArgumentException("金额最多两位小数");
        }
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money multiply(int multiplier) {
        return new Money(this.amount.multiply(BigDecimal.valueOf(multiplier))
                                     .setScale(2, RoundingMode.HALF_UP),
                         this.currency);
    }

    private void requireSameCurrency(Money other) {
        if (this.currency != other.currency) {
            throw new IllegalArgumentException("币种不一致");
        }
    }

    public enum Currency { CNY, USD, EUR }
}