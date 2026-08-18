package com.mall;

import com.mall.common.BusinessException;
import com.mall.common.ResultCode;
import com.mall.dto.CreateOrderDTO;
import com.mall.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import jakarta.annotation.Resource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 订单服务冒烟测试。
 * 运行前提：本地 MySQL(mall 库) + Redis 已启动，且 t_product 中有 id=1 的商品。
 * 生产环境请用 H2 + testcontainers 做真正的单元测试（参考学习文档第 24 章）。
 */
@SpringBootTest
@ActiveProfiles("dev")
class OrderServiceTest {

    @Resource
    private OrderService orderService;

    @Test
    void createOrder_shouldReturnOrderNo() {
        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setProductId(1L);
        dto.setQuantity(1);
        String orderNo = orderService.createOrder(1L, dto);
        assertNotNull(orderNo);
        System.out.println("下单成功，订单号: " + orderNo);
    }

    @Test
    void createOrder_shouldFailWhenStockNotEnough() {
        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setProductId(1L);
        dto.setQuantity(999999); // 超过库存，应抛库存不足
        assertThrows(BusinessException.class, () -> orderService.createOrder(1L, dto));
    }
}
