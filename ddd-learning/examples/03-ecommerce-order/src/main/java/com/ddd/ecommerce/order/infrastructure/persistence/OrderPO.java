package com.ddd.ecommerce.order.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/** 订单持久化对象（基础设施层 PO）。 */
@Data
@TableName("t_order")
public class OrderPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;
    private Long customerId;
    private String status;
    private BigDecimal totalAmount;
    private String currency;
    private Instant createdAt;
    private Instant updatedAt;
}