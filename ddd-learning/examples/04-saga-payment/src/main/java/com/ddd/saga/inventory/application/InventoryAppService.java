package com.ddd.saga.inventory.application;

import com.ddd.saga.inventory.domain.Inventory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 库存应用服务（Saga 端口实现）。
 *
 * <p>Saga 协调器通过本类调用 reserve / commit / release。
 */
@Service
public class InventoryAppService {

    /** 实际工程用仓储；示例用内存 Map。 */
    private final java.util.Map<Long, Inventory> store = new java.util.concurrent.ConcurrentHashMap<>();

    @Transactional
    public void reserve(Long productId, int quantity) {
        Inventory inv = store.computeIfAbsent(productId, id -> new Inventory(id, 1000));
        inv.reserve(quantity);
    }

    @Transactional
    public void commit(Long productId, int quantity) {
        Inventory inv = store.get(productId);
        if (inv == null) throw new IllegalStateException("库存不存在：" + productId);
        inv.commit(quantity);
    }

    @Transactional
    public void release(Long productId, int quantity) {
        Inventory inv = store.get(productId);
        if (inv == null) return;     // 补偿幂等：不存在不抛异常
        inv.release(quantity);
    }
}