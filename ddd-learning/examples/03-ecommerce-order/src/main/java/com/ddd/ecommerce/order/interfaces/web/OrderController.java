package com.ddd.ecommerce.order.interfaces.web;

import com.ddd.ecommerce.order.application.command.SubmitOrderCommand;
import com.ddd.ecommerce.order.application.service.OrderAppService;
import com.ddd.ecommerce.order.domain.model.Money;
import com.ddd.ecommerce.order.domain.model.Order;
import com.ddd.ecommerce.order.domain.model.OrderId;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 订单 REST Controller（接口层）。
 *
 * <p>只依赖应用服务 OrderAppService，<b>绝不</b>直接注入仓储或聚合根。
 * <p>所有跨边界数据用 Map / DTO 转换，不直接返回 Order 实体。
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderAppService appService;

    public OrderController(OrderAppService appService) {
        this.appService = appService;
    }

    @PostMapping
    public Map<String, Object> submit(@RequestBody SubmitOrderRequest req) {
        SubmitOrderCommand cmd = new SubmitOrderCommand(
            req.customerId(),
            req.items().stream().map(i ->
                new SubmitOrderCommand.OrderItemCommand(
                    i.productId(), i.productName(), i.quantity(),
                    new Money(new BigDecimal(i.unitPrice()), Money.Currency.CNY)
                )
            ).toList()
        );
        OrderId orderId = appService.submit(cmd);
        return Map.of("code", 200, "data", Map.of("orderId", orderId.value()));
    }

    @GetMapping("/{id}")
    public Map<String, Object> findById(@PathVariable Long id) {
        Order order = appService.findById(new OrderId(id));
        return Map.of(
            "code", 200,
            "data", Map.of(
                "orderId", order.id().value(),
                "orderNo", order.orderNo(),
                "status", order.status().name(),
                "total", order.total().amount()
            )
        );
    }

    @PostMapping("/{id}/pay")
    public Map<String, Object> pay(@PathVariable Long id) {
        appService.pay(new OrderId(id));
        return Map.of("code", 200, "msg", "支付成功");
    }

    @PostMapping("/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable Long id, @RequestBody CancelRequest req) {
        appService.cancel(new OrderId(id), req.reason());
        return Map.of("code", 200, "msg", "取消成功");
    }

    @PostMapping("/{id}/ship")
    public Map<String, Object> ship(@PathVariable Long id) {
        appService.ship(new OrderId(id));
        return Map.of("code", 200, "msg", "发货成功");
    }

    @PostMapping("/{id}/complete")
    public Map<String, Object> complete(@PathVariable Long id) {
        appService.complete(new OrderId(id));
        return Map.of("code", 200, "msg", "完成成功");
    }

    /** 入参 DTO。 */
    public record SubmitOrderRequest(Long customerId, java.util.List<Item> items) {
        public record Item(Long productId, String productName, int quantity, String unitPrice) {}
    }
    public record CancelRequest(String reason) {}
}