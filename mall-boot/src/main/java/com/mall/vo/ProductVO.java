package com.mall.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品视图对象：只暴露前端需要的字段，避免把内部字段（deleted 等）泄露。
 */
@Data
public class ProductVO {

    private Long id;

    private String name;

    private String description;

    private BigDecimal price;

    /** 剩余库存（列表页展示，秒杀页用缓存计数） */
    private Integer stock;
}
