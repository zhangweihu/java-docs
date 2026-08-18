package com.mall.controller;

import com.mall.common.Result;
import com.mall.dto.LoginDTO;
import com.mall.dto.RegisterDTO;
import com.mall.interceptor.JwtInterceptor;
import com.mall.service.UserService;
import com.mall.vo.LoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户模块：注册 / 登录 / 登出。
 * Controller 只做参数接收与结果返回，业务全在 Service。
 */
@Tag(name = "用户模块")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "注册")
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterDTO dto) {
        userService.register(dto);
        return Result.ok();
    }

    @Operation(summary = "登录，返回 JWT Token")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.ok(userService.login(dto));
    }

    @Operation(summary = "登出（Token 拉黑）")
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = JwtInterceptor.HEADER, required = false) String auth) {
        // 拦截器已保证进入此方法时 Token 合法
        String token = auth != null ? auth.replace(JwtInterceptor.PREFIX, "") : null;
        userService.logout(token);
        return Result.ok();
    }
}
