package com.mall.util;

import com.mall.common.BusinessException;
import com.mall.common.ResultCode;
import com.mall.config.JwtConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * JWT 工具类：签发与解析令牌。
 * Token 结构：Header.Payload.Signature，Payload 内放 userId、username，
 * 服务端不保存会话（无状态），天然支持水平扩展。
 */
@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final JwtConfig jwtConfig;

    /** 为用户签发 Token */
    public String createToken(Long userId, String username) {
        Date now = new Date();
        Date expire = new Date(now.getTime() + jwtConfig.getExpireHours() * 3600_000L);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(now)
                .expiration(expire)
                .signWith(jwtConfig.getSecretKey())
                .compact();
    }

    /** Token 过期分钟数（登出拉黑时复用） */
    public long expireMinutes() {
        return jwtConfig.getExpireHours() * 60;
    }

    /**
     * 解析 Token；过期或签名错误统一抛出 401，
     * 由全局异常处理器转成 Result 返回。
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(jwtConfig.getSecretKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
    }
}
