package com.mall.product.service.impl;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.common.BusinessException;
import com.mall.common.Result;
import com.mall.product.entity.Product;
import com.mall.product.mapper.ProductMapper;
import com.mall.product.service.ProductService;
import com.mall.product.sentinel.ProductSentinelBlockHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品服务实现。
 */
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;

    @Override
    public Result<List<Product>> list() {
        return Result.ok(productMapper.selectList(
                new LambdaQueryWrapper<Product>().eq(Product::getStatus, 1)));
    }

    @Override
    @SentinelResource(value = "product:detail",
            blockHandlerClass = ProductSentinelBlockHandler.class,
            blockHandler = "detailBlock")
    public Result<Product> detail(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null || product.getStatus() != 1) {
            throw new BusinessException(404, "商品不存在");
        }
        return Result.ok(product);
    }

    @Override
    public Result<BigDecimal> getPrice(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            return Result.fail(404, "商品不存在");
        }
        return Result.ok(product.getPrice());
    }

    @Override
    public Result<Void> deductStock(Long productId, Integer quantity) {
        int affected = productMapper.deductStock(productId, quantity);
        if (affected == 0) {
            // 失败返回业务码 4003，由订单服务感知并触发 Seata 全局回滚
            return Result.fail(4003, "库存不足");
        }
        return Result.ok();
    }
}
