package com.ddd.modular.payment.domain.repository;

import com.ddd.modular.payment.domain.model.Account;

import java.util.Optional;

/** 账户仓储接口。 */
public interface AccountRepository {
    void save(Account account);
    Optional<Account> findByUserId(Long userId);
}