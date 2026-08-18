package com.mall.common;

import lombok.Getter;

/**
 * 业务异常：Service 层校验失败时直接抛出，由全局异常处理器统一转成 Result。
 * 使用示例：throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH);
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
