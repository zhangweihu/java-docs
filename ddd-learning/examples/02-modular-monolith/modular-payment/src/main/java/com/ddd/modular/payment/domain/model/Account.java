package com.ddd.modular.payment.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 账户聚合（充血）。
 *
 * <p>不变量：balance ≥ 0；frozen ≥ 0；balance + frozen = 总额。
 * <p>行为：deposit / freeze / unfreeze / deduct。
 */
public class Account {

    private final Long id;
    private final Long userId;
    private BigDecimal balance;
    private BigDecimal frozen;
    private final String currency;
    private Instant updatedAt;

    public Account(Long id, Long userId, BigDecimal initialBalance, String currency, Instant updatedAt) {
        if (initialBalance.signum() < 0) {
            throw new IllegalArgumentException("初始余额不能为负");
        }
        this.id = id;
        this.userId = userId;
        this.balance = initialBalance;
        this.frozen = BigDecimal.ZERO;
        this.currency = currency;
        this.updatedAt = updatedAt;
    }

    /** 充值。 */
    public void deposit(BigDecimal amount) {
        requirePositive(amount);
        this.balance = this.balance.add(amount);
        this.updatedAt = Instant.now();
    }

    /** 冻结（用于下单时的"预扣"）：balance → frozen。 */
    public void freeze(BigDecimal amount) {
        requirePositive(amount);
        if (balance.compareTo(amount) < 0) {
            throw new com.ddd.modular.common.exception.BusinessException
                .InsufficientBalanceException(userId);
        }
        this.balance = this.balance.subtract(amount);
        this.frozen = this.frozen.add(amount);
        this.updatedAt = Instant.now();
    }

    /** 解冻（取消订单时）：frozen → balance。 */
    public void unfreeze(BigDecimal amount) {
        requirePositive(amount);
        if (frozen.compareTo(amount) < 0) {
            throw new IllegalStateException("冻结金额不足");
        }
        this.frozen = this.frozen.subtract(amount);
        this.balance = this.balance.add(amount);
        this.updatedAt = Instant.now();
    }

    /** 扣款（订单确认后）：frozen → 实际扣款。 */
    public void deduct(BigDecimal amount) {
        requirePositive(amount);
        if (frozen.compareTo(amount) < 0) {
            throw new IllegalStateException("冻结金额不足");
        }
        this.frozen = this.frozen.subtract(amount);
        this.updatedAt = Instant.now();
    }

    private void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("金额必须为正");
        }
    }

    public Long id() { return id; }
    public Long userId() { return userId; }
    public BigDecimal balance() { return balance; }
    public BigDecimal frozen() { return frozen; }
    public String currency() { return currency; }
    public Instant updatedAt() { return updatedAt; }
}