package com.mall.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.product.entity.Product;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 商品数据访问接口。
 * deductStock 乐观扣减：SQL 带 stock >= quantity 条件，防超卖核心。
 */
public interface ProductMapper extends BaseMapper<Product> {

    @Update("UPDATE t_product SET stock = stock - #{quantity} " +
            "WHERE id = #{productId} AND stock >= #{quantity} AND deleted = 0")
    int deductStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);
}
