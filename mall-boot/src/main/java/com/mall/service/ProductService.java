package com.mall.service;

import com.mall.entity.Product;
import com.mall.vo.ProductVO;

import java.util.List;

/**
 * 商品服务接口：列表、详情（含缓存）、管理端新增。
 */
public interface ProductService {

    /** 商品列表（公开接口） */
    List<ProductVO> list();

    /** 商品详情：Cache Aside 模式，带互斥锁防击穿 */
    ProductVO detail(Long id);

    /** 管理端新增商品（演示用，不做权限细分） */
    void add(Product product);
}
