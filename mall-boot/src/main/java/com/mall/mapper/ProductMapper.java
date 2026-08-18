package com.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.entity.Product;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 商品 Mapper。
 * 这里演示"乐观扣减库存"的原生 SQL：
 *   where stock >= 数量 保证不超卖；受影响行数=0 表示库存不足或并发抢不到。
 */
public interface ProductMapper extends BaseMapper<Product> {

    @Update("UPDATE t_product SET stock = stock - #{quantity} " +
            "WHERE id = #{productId} AND stock >= #{quantity} AND deleted = 0")
    int deductStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);
}
