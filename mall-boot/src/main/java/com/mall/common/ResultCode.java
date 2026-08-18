package com.mall.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一返回码枚举：前端根据 code 判断业务是否成功。
 *
 * 约定：200 成功；4xxx 客户端错误（参数/鉴权/库存等）；5xxx 服务端错误。
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权访问"),
    NOT_FOUND(404, "资源不存在"),

    // ---- 业务错误（4xxx）----
    USERNAME_EXISTS(4001, "用户名已存在"),
    LOGIN_FAILED(4002, "用户名或密码错误"),
    STOCK_NOT_ENOUGH(4003, "库存不足"),
    ORDER_STATUS_ERROR(4004, "订单状态不允许该操作"),
    FILE_TYPE_ERROR(4005, "文件类型不支持"),
    FREQUENT_REQUEST(4006, "操作过于频繁，请稍后再试"),

    // ---- 服务端错误（5xxx）----
    SYSTEM_ERROR(5000, "系统繁忙，请稍后再试");

    private final int code;
    private final String message;
}
