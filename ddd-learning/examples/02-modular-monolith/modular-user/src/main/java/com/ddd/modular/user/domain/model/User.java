package com.ddd.modular.user.domain.model;

import java.time.Instant;
import java.util.regex.Pattern;

/**
 * 用户聚合根（充血实现）。
 *
 * <p>模块：{@code modular-user}
 * <p>对外暴露：通过 {@code application.api.UserQueryService}，不直接暴露本类。
 */
public class User {

    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$");

    private final Long id;
    private final String username;
    private String email;
    private final Instant createdAt;

    public User(Long id, String username, String email, Instant createdAt) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("邮箱格式不合法：" + email);
        }
        this.id = id;
        this.username = username;
        this.email = email;
        this.createdAt = createdAt;
    }

    public void changeEmail(String newEmail) {
        if (!EMAIL_PATTERN.matcher(newEmail).matches()) {
            throw new IllegalArgumentException("邮箱格式不合法：" + newEmail);
        }
        this.email = newEmail;
    }

    public Long id() { return id; }
    public String username() { return username; }
    public String email() { return email; }
    public Instant createdAt() { return createdAt; }
}