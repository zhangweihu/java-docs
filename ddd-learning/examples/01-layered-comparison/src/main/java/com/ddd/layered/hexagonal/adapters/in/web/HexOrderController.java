package com.ddd.layered.hexagonal.adapters.in.web;

import com.ddd.layered.hexagonal.application.SubmitOrderCommand;
import com.ddd.layered.hexagonal.ports.in.SubmitOrderPort;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 入口适配器（Inbound Adapter / Driving Adapter）。
 *
 * <p>REST Controller 作为"主适配器"，调用 {@link SubmitOrderPort} 入口端口。
 * <p>路径前缀：{@code /hex/orders}
 */
@RestController
@RequestMapping("/hex/orders")
public class HexOrderController {

    private final SubmitOrderPort submitOrderPort;

    public HexOrderController(SubmitOrderPort submitOrderPort) {
        this.submitOrderPort = submitOrderPort;
    }

    @PostMapping
    public Map<String, Object> submit(@RequestBody SubmitOrderRequest req) {
        SubmitOrderCommand cmd = new SubmitOrderCommand(
            req.customerId(),
            req.items().stream().map(i ->
                new SubmitOrderCommand.ItemCommand(
                    i.productId(), i.productName(), i.quantity(), i.unitPrice()
                )
            ).toList()
        );
        Long orderId = submitOrderPort.submit(cmd);
        return Map.of("code", 200, "data", Map.of("orderId", orderId));
    }

    /** Controller 入参 DTO。 */
    public record SubmitOrderRequest(
        Long customerId,
        java.util.List<Item> items
    ) {
        public record Item(Long productId, String productName, int quantity,
                           java.math.BigDecimal unitPrice) {}
    }
}