package com.mall.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 网关 Sentinel 兜底响应配置。
 *
 * <p>流控规则（路由级 + API 分组级）已改为「规则配置中心化」：
 * 通过 {@code spring.cloud.sentinel.datasource} 从 Nacos 拉取（见 application.yml），
 * 本类只负责自定义被限流时的兜底 JSON 响应（Sentinel 默认网关兜底是 429 + 空 body）。</p>
 *
 * <p>规则文件：{@code docker/nacos-config/mall-gateway-flow-rules.json}
 * / {@code mall-gateway-api-group-rules.json}，Nacos 控制台修改后实时生效。</p>
 */
@Slf4j
@Configuration
public class GatewaySentinelConfig {

    @PostConstruct
    public void initBlockHandler() {
        GatewayCallbackManager.setBlockHandler((exchange, t) -> {
            String body = "{\"code\":429,\"message\":\"请求过于频繁，已被网关限流（Sentinel）\",\"data\":null}";
            return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body);
        });
        log.info("网关 Sentinel 兜底响应已启用：限流统一返回 429 + 自定义 JSON");
    }
}
