package com.mall.user.service;

import com.mall.common.Result;
import com.mall.user.entity.User;

/**
 * 用户服务接口。
 */
public interface UserService {

    /** 注册（网关白名单放行） */
    Result<Void> register(String username, String password, String phone);

    /** 登录：校验密码，返回 JWT（与单体共用同一套签发逻辑与密钥） */
    Result<String> login(String username, String password);

    /** 内部接口：按 ID 查昵称（供订单服务 Feign 调用） */
    Result<String> getNickname(Long userId);

    /** 内部接口：按 ID 查完整用户（供鉴权/管理使用） */
    Result<User> getById(Long userId);
}
