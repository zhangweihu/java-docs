package com.ddd.layered.ddd.interfaces.web;

import com.ddd.layered.ddd.application.command.SubmitOrderCommand;
import com.ddd.layered.ddd.application.service.DddOrderAppService;
import com.ddd.layered.ddd.domain.model.Order;
import com.ddd.layered.ddd.domain.model.OrderId;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * DDD 四层风格的 Controller。
 *
 * <p>路径前缀：{@code /ddd/orders}
 * <p>只依赖应用服务接口（或命令对象），不直接触碰领域实体在外的字段。
 */
@RestController
@RequestMapping("/ddd/orders")
public class DddOrderController {

    private final DddOrderAppService appService;

    public DddOrderController(DddOrderAppService appService) {
        this.appService = appService;
    }

    @PostMapping
    public Map<String, Object> submit(@RequestBody SubmitOrderRequest req) {
        SubmitOrderCommand domainCmd = new SubmitOrderCommand(
            req.customerId(),
            req.items().stream().map(i ->
                new SubmitOrderCommand.OrderItemCommand(
                    i.productId(), i.productName(), i.quantity(), i.unitPrice())
            ).toList()
        );
        OrderId id = appService.submit(domainCmd);
        return Map.of("code", 200, "data", Map.of("orderId", id.value()));
    }

    @GetMapping("/{id}")
    public Map<String, Object> findById(@PathVariable Long id) {
        Order order = appService.findById(new OrderId(id));
        return Map.of(
            "code", 200,
            "data", Map.of(
                "orderId", order.id().value(),
                "status", order.status().name(),
                "total", order.total().amount()
            )
        );
    }

    /** Controller 入参 DTO（仅在接口层定义）。 */
    public record SubmitOrderRequest(
        Long customerId,
        java.util.List<Item> items
    ) {
        public record Item(Long productId, String productName, int quantity,
                           com.ddd.layered.ddd.domain.model.Money unitPrice) {}
    }
}