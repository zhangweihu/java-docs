package com.ddd.saga.payment.application;

import com.ddd.saga.payment.domain.Account;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 支付应用服务（Saga 端口实现）。
 *
 * <p>核心：freeze / unfreeze / deduct 三个方法。
 * <p>幂等性：基于 {@code transactionId}，实际工程用 idempotency_key 唯一约束。
 */
@Service
public class PaymentAppService {

    private final ConcurrentHashMap<Long, Account> accounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> idempotency = new ConcurrentHashMap<>();

    @Transactional
    public String freeze(Long userId, BigDecimal amount) {
        String txId = "T" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6);
        idempotency.putIfAbsent(txId, true);
        Account account = accounts.computeIfAbsent(userId, id -> new Account(id, new BigDecimal("10000")));
        account.freeze(amount);
        return txId;
    }

    @Transactional
    public void unfreeze(Long userId, BigDecimal amount, String transactionId) {
        if (idempotency.putIfAbsent("unfreeze_" + transactionId, true) != null) return;
        Account account = accounts.get(userId);
        if (account == null) return;
        account.unfreeze(amount);
    }

    @Transactional
    public void deduct(Long userId, BigDecimal amount, String transactionId) {
        if (idempotency.putIfAbsent("deduct_" + transactionId, true) != null) return;
        Account account = accounts.get(userId);
        if (account == null) throw new IllegalStateException("账户不存在");
        account.deduct(amount);
    }
}