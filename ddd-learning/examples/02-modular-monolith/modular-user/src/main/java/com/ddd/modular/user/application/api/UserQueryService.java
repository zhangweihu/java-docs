package com.ddd.modular.user.application.api;

import java.util.Optional;

/**
 * 用户查询 API（对外暴露）。
 *
 * <p>其他模块（如订单模块）只能注入此接口，不能注入 UserRepository 或 UserAppService。
 * <p>位于 {@code application.api} 包，是用户上下文对外的唯一接口。
 */
public interface UserQueryService {

    Optional<UserView> findById(Long userId);

    Optional<UserView> findByUsername(String username);

    /** 读视图 DTO。 */
    record UserView(Long id, String username, String email) {}
}