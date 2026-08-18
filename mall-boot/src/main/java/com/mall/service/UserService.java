package com.mall.service;

import com.mall.dto.LoginDTO;
import com.mall.dto.RegisterDTO;
import com.mall.vo.LoginVO;

/**
 * 用户服务接口：注册、登录、登出。
 */
public interface UserService {

    /** 注册：用户名查重 + 密码 BCrypt 加密入库 */
    void register(RegisterDTO dto);

    /** 登录：校验密码 + 签发 JWT */
    LoginVO login(LoginDTO dto);

    /** 登出：把当前 Token 拉黑，防止其在剩余有效期内被冒用 */
    void logout(String token);
}
