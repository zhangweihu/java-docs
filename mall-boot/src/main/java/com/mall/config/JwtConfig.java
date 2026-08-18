package com.mall.config;

import io.jsonwebtoken.security.Keys;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * JWT 配置：从 application.yml 读取 mall.jwt.* 配置。
 * 注意：生产环境 secret 必须>=32字节并通过环境变量注入，禁止硬编码。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "mall.jwt")
public class JwtConfig {

    /** 签名密钥（HS256 要求 >= 32 字节） */
    private String secret;

    /** 过期时间，单位：小时 */
    private Long expireHours;

    /** 生成 HS256 签名密钥对象 */
    public SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
