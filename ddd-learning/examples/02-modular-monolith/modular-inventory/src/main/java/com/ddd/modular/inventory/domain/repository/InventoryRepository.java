package com.ddd.modular.inventory.domain.repository;

import com.ddd.modular.inventory.domain.model.Inventory;

import java.util.Optional;

/** 库存仓储接口。 */
public interface InventoryRepository {
    void save(Inventory inventory);
    Optional<Inventory> findByProductId(Long productId);
}