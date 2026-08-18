package com.mall.common;

import lombok.Data;

/**
 * 统一响应体（微服务版）：网关/各服务/前端三方共用，保证响应结构全局一致。
 */
@Data
public class Result<T> {

    private int code;
    private String message;
    private T data;

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = 200;
        r.message = "操作成功";
        r.data = data;
        return r;
    }

    public static <T> Result<T> fail(int code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        return r;
    }

    /** 判断业务是否成功（Feign 消费方判断契约调用结果用） */
    public boolean isSuccess() {
        return this.code == 200;
    }
}
