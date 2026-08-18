package com.mall.common;

import lombok.Data;

/**
 * 统一响应体：所有 Controller 必须返回本类型，前端据此统一解析。
 *
 * 用法：
 *   Result.ok()                 无数据成功
 *   Result.ok(data)             携带数据成功
 *   Result.fail(ResultCode.XX)  业务失败
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
        r.code = ResultCode.SUCCESS.getCode();
        r.message = ResultCode.SUCCESS.getMessage();
        r.data = data;
        return r;
    }

    public static <T> Result<T> fail(ResultCode code) {
        return fail(code.getCode(), code.getMessage());
    }

    public static <T> Result<T> fail(int code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        return r;
    }
}
