package com.ddd.modular.user.application.api;

import com.ddd.modular.user.domain.model.User;
import com.ddd.modular.user.domain.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** UserQueryService 实现（读侧 CQRS 模式：直查仓储，不走应用服务）。 */
@Service
public class UserQueryServiceImpl implements UserQueryService {

    private final UserRepository repository;

    public UserQueryServiceImpl(UserRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<UserView> findById(Long userId) {
        return repository.findById(userId).map(this::toView);
    }

    @Override
    public Optional<UserView> findByUsername(String username) {
        return repository.findByUsername(username).map(this::toView);
    }

    private UserView toView(User user) {
        return new UserView(user.id(), user.username(), user.email());
    }
}