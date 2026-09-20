package com.ddd.layered.clean.interfaces.web;

import com.ddd.layered.clean.application.SubmitOrderUseCase;
import com.ddd.layered.clean.application.command.SubmitOrderCommand;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 整洁架构风格的 Controller。
 *
 * <p>依赖 {@link SubmitOrderUseCase} <b>接口</b>而非具体类——这是整洁与 DDD 四层的关键差异。
 * <p>路径前缀：{@code /clean/orders}
 */
@RestController
@RequestMapping("/clean/orders")
public class CleanOrderController {

    private final SubmitOrderUseCase submitOrderUseCase;

    public CleanOrderController(SubmitOrderUseCase submitOrderUseCase) {
        this.submitOrderUseCase = submitOrderUseCase;
    }

    @PostMapping
    public Map<String, Object> submit(@RequestBody SubmitOrderRequest req) {
        SubmitOrderCommand cmd = new SubmitOrderCommand(
            req.customerId(),
            req.items().stream().map(i ->
                new SubmitOrderCommand.ItemCommand(
                    i.productId(), i.productName(), i.quantity(),
                    i.unitPrice(), i.currency()
                )
            ).toList()
        );
        Long orderId = submitOrderUseCase.execute(cmd);
        return Map.of("code", 200, "data", Map.of("orderId", orderId));
    }

    /** Controller 入参 DTO。 */
    public record SubmitOrderRequest(
        Long customerId,
        java.util.List<Item> items
    ) {
        public record Item(Long productId, String productName, int quantity,
                           java.math.BigDecimal unitPrice, String currency) {}
    }
}