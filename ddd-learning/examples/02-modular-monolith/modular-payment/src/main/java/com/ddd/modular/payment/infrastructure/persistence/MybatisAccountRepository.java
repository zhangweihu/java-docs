package com.ddd.modular.payment.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ddd.modular.payment.domain.model.Account;
import com.ddd.modular.payment.domain.repository.AccountRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MybatisAccountRepository implements AccountRepository {

    private final AccountMapper mapper;

    public MybatisAccountRepository(AccountMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(Account account) {
        AccountPO po = toPO(account);
        if (po.getId() == null) {
            mapper.insert(po);
        } else {
            mapper.updateById(po);
        }
    }

    @Override
    public Optional<Account> findByUserId(Long userId) {
        return Optional.ofNullable(
            mapper.selectOne(new LambdaQueryWrapper<AccountPO>().eq(AccountPO::getUserId, userId))
        ).map(this::toDomain);
    }

    private AccountPO toPO(Account a) {
        AccountPO po = new AccountPO();
        po.setId(a.id());
        po.setUserId(a.userId());
        po.setBalance(a.balance());
        po.setFrozen(a.frozen());
        po.setCurrency(a.currency());
        po.setUpdatedAt(a.updatedAt());
        return po;
    }

    private Account toDomain(AccountPO po) {
        return new Account(po.getId(), po.getUserId(),
            po.getBalance(), po.getCurrency(), po.getUpdatedAt());
    }
}