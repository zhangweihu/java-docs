package com.mall.product.service;

import com.mall.common.Result;
import com.mall.product.entity.Product;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品服务接口。
 */
public interface ProductService {

    /** 商品列表（公开） */
    Result<List<Product>> list();

    /** 商品详情（公开） */
    Result<Product> detail(Long id);

    /** 内部接口：查询单价快照（订单服务 Feign 调用） */
    Result<BigDecimal> getPrice(Long productId);

    /** 内部接口：乐观扣减库存（订单服务 Feign 调用，跨服务事务由 Seata 协调） */
    Result<Void> deductStock(Long productId, Integer quantity);
}
