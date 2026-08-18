package com.mall.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.common.BusinessException;
import com.mall.common.Result;
import com.mall.user.entity.User;
import com.mall.user.mapper.UserMapper;
import com.mall.user.service.UserService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * 用户服务实现。
 * JWT 密钥与网关保持一致（环境变量 JWT_SECRET 注入），
 * 网关负责验签，用户服务负责签发。
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private final UserMapper userMapper;

    @Value("${mall.jwt.secret}")
    private String jwtSecret;

    @Value("${mall.jwt.expire-hours:24}")
    private Long expireHours;

    @Override
    public Result<Void> register(String username, String password, String phone) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (count > 0) {
            throw new BusinessException(4001, "用户名已存在");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(ENCODER.encode(password));
        user.setNickname(username);
        user.setPhone(phone);
        user.setStatus(0);
        userMapper.insert(user);
        return Result.ok();
    }

    @Override
    public Result<String> login(String username, String password) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null || !ENCODER.matches(password, user.getPassword())) {
            throw new BusinessException(4002, "用户名或密码错误");
        }
        Date now = new Date();
        String token = Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireHours * 3600_000L))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
        return Result.ok(token);
    }

    @Override
    public Result<String> getNickname(Long userId) {
        User user = userMapper.selectById(userId);
        return user == null ? Result.fail(404, "用户不存在") : Result.ok(user.getNickname());
    }

    @Override
    public Result<User> getById(Long userId) {
        return Result.ok(userMapper.selectById(userId));
    }
}
