package com.ddd.modular.payment.application.service;

import com.ddd.modular.common.exception.BusinessException;
import com.ddd.modular.payment.domain.model.Account;
import com.ddd.modular.payment.domain.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 支付应用服务（资金操作）。
 *
 * <p>核心：deposit / freeze / unfreeze / deduct。
 * <p>所有金额操作幂等基于 {@code transactionId}，实际工程配合 Saga 协调。
 */
@Service
public class PaymentAppService {

    private final AccountRepository repository;

    public PaymentAppService(AccountRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void deposit(Long userId, BigDecimal amount) {
        Account account = repository.findByUserId(userId)
            .orElseThrow(() -> new BusinessException.UserNotFoundException(userId));
        account.deposit(amount);
        repository.save(account);
    }

    @Transactional
    public void freeze(Long userId, BigDecimal amount) {
        Account account = repository.findByUserId(userId)
            .orElseThrow(() -> new BusinessException.UserNotFoundException(userId));
        account.freeze(amount);
        repository.save(account);
    }

    @Transactional
    public void unfreeze(Long userId, BigDecimal amount) {
        Account account = repository.findByUserId(userId)
            .orElseThrow(() -> new BusinessException.UserNotFoundException(userId));
        account.unfreeze(amount);
        repository.save(account);
    }

    @Transactional
    public void deduct(Long userId, BigDecimal amount) {
        Account account = repository.findByUserId(userId)
            .orElseThrow(() -> new BusinessException.UserNotFoundException(userId));
        account.deduct(amount);
        repository.save(account);
    }

    /** 初始化账户（用户注册时调用）。 */
    @Transactional
    public void initialize(Long userId) {
        if (repository.findByUserId(userId).isPresent()) {
            throw new BusinessException("ACCOUNT_EXISTS", "账户已存在：" + userId);
        }
        repository.save(new Account(null, userId, BigDecimal.ZERO, "CNY", Instant.now()));
    }
}