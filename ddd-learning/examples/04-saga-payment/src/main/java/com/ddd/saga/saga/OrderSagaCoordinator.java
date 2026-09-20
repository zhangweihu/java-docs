package com.ddd.saga.saga;

import com.ddd.saga.inventory.application.InventoryAppService;
import com.ddd.saga.order.application.OrderAppService;
import com.ddd.saga.payment.application.PaymentAppService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.statemachine.StateMachineFactory;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Saga 协调器（编排式）。
 *
 * <p>状态机管理 Saga 生命周期：
 * <pre>
 *   START ──► INVENTORY_RESERVED ──► AMOUNT_FROZEN ──► [外部触发 ORDER_PAID] ──► AMOUNT_DEDUCTED ──► COMPLETED
 *      │                                       │
 *      └──► CANCELLED ◄─── 失败补偿 ────────────┘
 * </pre>
 */
@Service
public class OrderSagaCoordinator {

    public enum SagaState { STARTED, INVENTORY_RESERVED, AMOUNT_FROZEN, PAID, AMOUNT_DEDUCTED, COMPLETED, CANCELLED }
    public enum SagaEvent { RESERVE_INVENTORY, FREEZE_AMOUNT, ORDER_PAID, DEDUCT, CANCEL }

    @Autowired private OrderAppService orderAppService;
    @Autowired private InventoryAppService inventoryAppService;
    @Autowired private PaymentAppService paymentAppService;

    /**
     * 启动 Saga。
     */
    public String startSaga(Long orderId, Long productId, Long userId, BigDecimal amount) {
        String sagaId = "S" + UUID.randomUUID().toString().substring(0, 8);

        try {
            // 步骤 1：库存预留
            inventoryAppService.reserve(productId, 1);

            // 步骤 2：金额冻结
            String transactionId = paymentAppService.freeze(userId, amount);

            // 步骤 3：等待用户付款（实际由 OrderAppService.markPaid 触发）
            // 这里只演示 Saga 主流程

            return sagaId;
        } catch (Exception e) {
            compensate(sagaId, productId, userId, amount, null);
            throw e;
        }
    }

    /** 付款后调用：扣款 + 库存扣减。 */
    @Transactional
    public void onOrderPaid(Long orderId, Long productId, Long userId,
                            BigDecimal amount, String freezeTransactionId) {
        try {
            // 步骤 4：扣款
            paymentAppService.deduct(userId, amount, freezeTransactionId);

            // 步骤 5：库存扣减
            inventoryAppService.commit(productId, 1);

            // 步骤 6：订单标记已付款
            orderAppService.markPaid(orderId);
        } catch (Exception e) {
            // 失败补偿
            paymentAppService.unfreeze(userId, amount, freezeTransactionId);
            inventoryAppService.release(productId, 1);
            throw e;
        }
    }

    /** Saga 失败时的补偿逻辑。 */
    private void compensate(String sagaId, Long productId, Long userId,
                            BigDecimal amount, String freezeTransactionId) {
        // 实际工程：按反向顺序补偿
        // 1. 如果已扣款 → 退款
        // 2. 如果已冻结 → 解冻
        // 3. 如果已预留 → 释放
        if (freezeTransactionId != null) {
            paymentAppService.unfreeze(userId, amount, freezeTransactionId);
        }
        inventoryAppService.release(productId, 1);
    }
}