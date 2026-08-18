package com.mall.common.feign;

import com.mall.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 用户服务 Feign 契约：订单服务需要回查用户信息时调用。
 * 路径 /internal/** 仅限服务间调用，网关不放行。
 */
@FeignClient(name = "mall-user", contextId = "userClient", path = "/internal/user")
public interface UserClient {

    /** 按 ID 查询用户昵称 */
    @GetMapping("/{id}/nickname")
    Result<String> getNickname(@PathVariable("id") Long userId);
}
