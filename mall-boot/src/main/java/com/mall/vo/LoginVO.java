package com.mall.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录结果 VO：令牌 + 用户基础信息。
 */
@Data
@AllArgsConstructor
public class LoginVO {

    private String token;

    private Long userId;

    private String username;

    private String nickname;
}
