package com.mall.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 统一鉴权全局过滤器（Ordered 优先级最高先执行）。
 *
 * 流程：
 *   1. 白名单（登录/注册/商品浏览/内部接口）直接放行
 *   2. 解析 Authorization: Bearer Token，失败返回 401
 *   3. 解析成功把 userId 放入请求头 X-User-Id 传递给下游服务，
 *      下游从请求头取用户（而不是前端传参），防越权
 *
 * 注意：本演示为简单校验签名+过期；生产建议结合 Redis 黑名单做登出失效。
 */
@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final String SECRET = "mall-cloud-jwt-secret-key-please-change-in-prod-0123456789";
    private static final List<String> WHITE_LIST = List.of(
            "/user/login", "/user/register",
            "/product/**",
            "/internal/**",          // 服务间内部接口一律不对外
            "/actuator/**"
    );
    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        // 1. 白名单放行
        if (WHITE_LIST.stream().anyMatch(p -> MATCHER.match(p, path))) {
            return chain.filter(exchange);
        }
        // 2. 取 Token 并校验
        String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }
        try {
            SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(auth.substring(7)).getPayload();
            // 3. 注入用户标识给下游
            ServerHttpRequest newRequest = exchange.getRequest().mutate()
                    .header("X-User-Id", claims.getSubject())
                    .header("X-Username", claims.get("username", String.class))
                    .build();
            return chain.filter(exchange.mutate().request(newRequest).build());
        } catch (Exception e) {
            log.warn("Token 校验失败: {}", e.getMessage());
            return unauthorized(exchange);
        }
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -100; // 数值越小优先级越高，保证最先执行
    }
}
