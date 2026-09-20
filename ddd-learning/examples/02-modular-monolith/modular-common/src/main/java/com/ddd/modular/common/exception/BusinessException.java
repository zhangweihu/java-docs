package com.ddd.modular.common.exception;

/** 业务异常基类。 */
public class BusinessException extends RuntimeException {

    private final String code;

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /** 订单不存在。 */
    public static class OrderNotFoundException extends BusinessException {
        public OrderNotFoundException(Long orderId) {
            super("ORDER_NOT_FOUND", "订单不存在：" + orderId);
        }
    }

    /** 用户不存在。 */
    public static class UserNotFoundException extends BusinessException {
        public UserNotFoundException(Long userId) {
            super("USER_NOT_FOUND", "用户不存在：" + userId);
        }
    }

    /** 库存不足。 */
    public static class InsufficientInventoryException extends BusinessException {
        public InsufficientInventoryException(Long productId) {
            super("INVENTORY_INSUFFICIENT", "库存不足：" + productId);
        }
    }

    /** 余额不足。 */
    public static class InsufficientBalanceException extends BusinessException {
        public InsufficientBalanceException(Long userId) {
            super("BALANCE_INSUFFICIENT", "余额不足：" + userId);
        }
    }
}