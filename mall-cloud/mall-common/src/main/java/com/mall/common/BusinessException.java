package com.mall.common;

import lombok.Getter;

/**
 * 业务异常（微服务版）：与单体内保持一致，各服务抛出后由本模块的
 * GlobalExceptionHandler 统一转成 Result。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
