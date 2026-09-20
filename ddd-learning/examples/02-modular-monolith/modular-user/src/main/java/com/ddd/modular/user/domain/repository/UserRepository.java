package com.ddd.modular.user.domain.repository;

import com.ddd.modular.user.domain.model.User;

import java.util.Optional;

/** 用户仓储接口（领域层定义）。 */
public interface UserRepository {
    void save(User user);
    Optional<User> findById(Long id);
    Optional<User> findByUsername(String username);
}