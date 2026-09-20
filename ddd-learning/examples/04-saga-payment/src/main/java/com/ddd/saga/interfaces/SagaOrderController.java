package com.ddd.saga.interfaces;

import com.ddd.saga.order.application.OrderAppService;
import com.ddd.saga.saga.OrderSagaCoordinator;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Saga 集成示例的 Controller。
 *
 * <p>演示一个端到端 Saga 流程：下单 → 启动 Saga → 付款 → 完成。
 */
@RestController
@RequestMapping("/saga")
public class SagaOrderController {

    private final OrderAppService orderAppService;
    private final OrderSagaCoordinator sagaCoordinator;

    public SagaOrderController(OrderAppService orderAppService, OrderSagaCoordinator sagaCoordinator) {
        this.orderAppService = orderAppService;
        this.sagaCoordinator = sagaCoordinator;
    }

    /** 步骤 1：创建订单 + 启动 Saga（库存预留 + 金额冻结）。 */
    @PostMapping("/orders")
    public Map<String, Object> placeOrder(@RequestBody PlaceOrderRequest req) {
        Long orderId = orderAppService.createOrder(req.customerId(), req.amount());

        String sagaId = sagaCoordinator.startSaga(
            orderId, req.productId(), req.customerId(), req.amount()
        );

        return Map.of("code", 200, "data", Map.of(
            "orderId", orderId,
            "sagaId", sagaId,
            "status", "SAGA_STARTED"
        ));
    }

    /** 步骤 2：模拟付款（Saga 继续：扣款 + 库存扣减）。 */
    @PostMapping("/orders/{orderId}/pay")
    public Map<String, Object> pay(@PathVariable Long orderId, @RequestParam Long productId,
                                   @RequestParam Long userId, @RequestParam BigDecimal amount,
                                   @RequestParam String freezeTransactionId) {
        sagaCoordinator.onOrderPaid(orderId, productId, userId, amount, freezeTransactionId);
        return Map.of("code", 200, "msg", "支付完成");
    }

    public record PlaceOrderRequest(Long customerId, Long productId, BigDecimal amount) {}
}