package com.ddd.layered.ddd.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 订单持久化对象（PO）。
 *
 * <p>PO 只属于基础设施层，<b>绝不</b>穿透到领域层或接口层。
 * <p>通过 {@link OrderPOAssembler} 与领域对象 {@link com.ddd.layered.ddd.domain.model.Order} 互转。
 */
@Data
@TableName("t_order")
public class OrderPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("order_no")
    private String orderNo;

    @TableField("customer_id")
    private Long customerId;

    @TableField("status")
    private String status;

    @TableField("total_amount")
    private BigDecimal totalAmount;

    @TableField("currency")
    private String currency;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;

    @TableField("deleted")
    private Integer deleted;
}