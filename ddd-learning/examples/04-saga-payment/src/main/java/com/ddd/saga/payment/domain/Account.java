package com.ddd.saga.payment.domain;

import java.math.BigDecimal;

/**
 * 账户聚合（Saga 集成版）。
 *
 * <p>含 balance + frozen 双字段，4 个行为方法。
 */
public class Account {

    private final Long id;
    private final Long userId;
    private BigDecimal balance;
    private BigDecimal frozen;

    public Account(Long userId, BigDecimal initialBalance) {
        this.userId = userId;
        this.balance = initialBalance;
        this.frozen = BigDecimal.ZERO;
        this.id = null;
    }

    public void freeze(BigDecimal amount) {
        if (balance.compareTo(amount) < 0) {
            throw new IllegalStateException("余额不足：" + userId);
        }
        this.balance = this.balance.subtract(amount);
        this.frozen = this.frozen.add(amount);
    }

    public void unfreeze(BigDecimal amount) {
        if (frozen.compareTo(amount) < 0) {
            throw new IllegalStateException("冻结不足：" + userId);
        }
        this.frozen = this.frozen.subtract(amount);
        this.balance = this.balance.add(amount);
    }

    public void deduct(BigDecimal amount) {
        if (frozen.compareTo(amount) < 0) {
            throw new IllegalStateException("冻结不足：" + userId);
        }
        this.frozen = this.frozen.subtract(amount);
    }

    public Long id() { return id; }
    public Long userId() { return userId; }
    public BigDecimal balance() { return balance; }
    public BigDecimal frozen() { return frozen; }
}