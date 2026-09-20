package com.ddd.layered.ddd.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 货币值对象（Java 17 record）。
 *
 * <p>用 record 实现的"金额 + 币种"二元组天然满足：
 * <ul>
 *   <li>不可变（record 默认 final 字段）</li>
 *   <li>全字段 equals/hashCode（record 默认）</li>
 *   <li>无 setter（record 默认）</li>
 * </ul>
 *
 * <p>所有金额运算必须返回新 Money，禁止原地修改。
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
            throw new IllegalArgumentException(
                "币种不一致：" + this.currency + " vs " + other.currency);
        }
    }

    /** 货币枚举（简化版）。 */
    public enum Currency {
        CNY, USD, EUR
    }
}