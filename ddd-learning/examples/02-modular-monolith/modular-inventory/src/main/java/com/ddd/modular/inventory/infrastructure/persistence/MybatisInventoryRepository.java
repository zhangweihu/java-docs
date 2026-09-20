package com.ddd.modular.inventory.infrastructure.persistence;

import com.ddd.modular.inventory.domain.model.Inventory;
import com.ddd.modular.inventory.domain.repository.InventoryRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MybatisInventoryRepository implements InventoryRepository {

    private final InventoryMapper mapper;

    public MybatisInventoryRepository(InventoryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(Inventory inventory) {
        InventoryPO po = toPO(inventory);
        if (mapper.selectById(inventory.productId()) == null) {
            mapper.insert(po);
        } else {
            mapper.updateById(po);
        }
    }

    @Override
    public Optional<Inventory> findByProductId(Long productId) {
        return Optional.ofNullable(mapper.selectById(productId)).map(this::toDomain);
    }

    private InventoryPO toPO(Inventory inv) {
        InventoryPO po = new InventoryPO();
        po.setProductId(inv.productId());
        po.setAvailable(inv.available());
        po.setReserved(inv.reserved());
        po.setSold(inv.sold());
        po.setUpdatedAt(inv.updatedAt());
        return po;
    }

    private Inventory toDomain(InventoryPO po) {
        return new Inventory(po.getProductId(), po.getAvailable(),
            po.getReserved(), po.getSold(), po.getUpdatedAt());
    }
}