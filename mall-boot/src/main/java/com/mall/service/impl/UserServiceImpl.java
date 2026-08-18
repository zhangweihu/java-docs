package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.common.BusinessException;
import com.mall.common.ResultCode;
import com.mall.dto.LoginDTO;
import com.mall.dto.RegisterDTO;
import com.mall.entity.User;
import com.mall.mapper.UserMapper;
import com.mall.service.UserService;
import com.mall.util.JwtUtil;
import com.mall.util.RedisKeyUtil;
import com.mall.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 用户服务实现。
 * 安全要点：
 *   - 密码永不明文存储，使用 BCrypt（内置随机盐，相同密码密文不同，抗彩虹表）
 *   - 登录失败不提示"用户名不存在/密码错误"，统一为"用户名或密码错误"，防账号探测
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void register(RegisterDTO dto) {
        // 1. 用户名查重（配合 t_user.username 唯一索引兜底，防并发重复注册）
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));
        if (count > 0) {
            throw new BusinessException(ResultCode.USERNAME_EXISTS);
        }
        // 2. BCrypt 加密入库
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(ENCODER.encode(dto.getPassword()));
        user.setNickname(dto.getUsername());
        user.setPhone(dto.getPhone());
        user.setStatus(0);
        userMapper.insert(user);
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));
        // 统一提示，不区分用户名错误与密码错误
        if (user == null || !ENCODER.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.LOGIN_FAILED);
        }
        if (user.getStatus() != null && user.getStatus() == 1) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        String token = jwtUtil.createToken(user.getId(), user.getUsername());
        return new LoginVO(token, user.getId(), user.getUsername(), user.getNickname());
    }

    @Override
    public void logout(String token) {
        // 登录拦截器已把 Bearer 前缀去掉，这里直接按 Token 拉黑（TTL 与 Token 过期时间一致）
        stringRedisTemplate.opsForValue().set(
                RedisKeyUtil.tokenBlacklist(token), "1", jwtUtil.expireMinutes(), TimeUnit.MINUTES);
    }
}
