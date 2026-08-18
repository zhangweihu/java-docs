package com.mall.product.sentinel;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.mall.common.Result;
import com.mall.product.entity.Product;

/**
 * 商品服务 Sentinel 兜底处理器。
 * 规则：必须是 public static 方法，签名 = 原方法参数 + BlockException。
 */
public final class ProductSentinelBlockHandler {

    /** 商品详情被限流时返回 */
    public static Result<Product> detailBlock(Long id, BlockException e) {
        return Result.fail(429, "商品详情请求过于频繁，已被 Sentinel 限流");
    }

    private ProductSentinelBlockHandler() {
    }
}
