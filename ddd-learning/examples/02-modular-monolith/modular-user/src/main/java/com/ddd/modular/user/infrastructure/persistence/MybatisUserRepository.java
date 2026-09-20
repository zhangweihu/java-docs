package com.ddd.modular.user.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ddd.modular.user.domain.model.User;
import com.ddd.modular.user.domain.repository.UserRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** 用户仓储 MyBatis 实现。 */
@Repository
public class MybatisUserRepository implements UserRepository {

    private final UserMapper mapper;

    public MybatisUserRepository(UserMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(User user) {
        UserPO po = toPO(user);
        if (po.getId() == null) {
            mapper.insert(po);
        } else {
            mapper.updateById(po);
        }
    }

    @Override
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(
            mapper.selectOne(new LambdaQueryWrapper<UserPO>().eq(UserPO::getUsername, username))
        ).map(this::toDomain);
    }

    private UserPO toPO(User user) {
        UserPO po = new UserPO();
        po.setId(user.id());
        po.setUsername(user.username());
        po.setEmail(user.email());
        po.setCreatedAt(user.createdAt());
        return po;
    }

    private User toDomain(UserPO po) {
        return new User(po.getId(), po.getUsername(), po.getEmail(), po.getCreatedAt());
    }
}