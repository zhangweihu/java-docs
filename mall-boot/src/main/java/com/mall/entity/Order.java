package com.mall.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体，对应表 t_order。
 * 状态机：0-待支付 -> 1-已支付 -> 2-已发货 -> 3-已完成；4-已取消
 * 幂等设计：order_no 建唯一索引，支付回调按状态流转，重复回调直接忽略。
 */
@Data
@TableName("t_order")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单号：雪花算法生成，全局唯一（业务主键） */
    private String orderNo;

    private Long userId;

    private Long productId;

    /** 单价快照（下单时价格，防止商品改价影响历史订单） */
    private BigDecimal price;

    private Integer quantity;

    private BigDecimal totalAmount;

    /** 0-待支付 1-已支付 2-已发货 3-已完成 4-已取消 */
    private Integer status;

    /** 支付时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime payTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
