package com.ddd.modular.user.application.service;

import com.ddd.modular.common.exception.BusinessException;
import com.ddd.modular.user.domain.model.User;
import com.ddd.modular.user.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 用户应用服务。
 *
 * <p>用例：注册、查询、修改邮箱。
 * <p>注意：本类在 {@code application.service} 包，internal。
 */
@Service
public class UserAppService {

    private final UserRepository repository;

    public UserAppService(UserRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Long register(String username, String email) {
        repository.findByUsername(username).ifPresent(u -> {
            throw new BusinessException("USER_EXISTS", "用户名已存在：" + username);
        });
        User user = new User(null, username, email, Instant.now());
        repository.save(user);
        return user.id();
    }

    @Transactional
    public void changeEmail(Long userId, String newEmail) {
        User user = repository.findById(userId)
            .orElseThrow(() -> new BusinessException.UserNotFoundException(userId));
        user.changeEmail(newEmail);
        repository.save(user);
    }
}