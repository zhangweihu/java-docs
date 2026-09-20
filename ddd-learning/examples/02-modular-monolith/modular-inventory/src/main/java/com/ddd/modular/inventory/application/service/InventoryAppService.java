package com.ddd.modular.inventory.application.service;

import com.ddd.modular.common.exception.BusinessException;
import com.ddd.modular.inventory.domain.model.Inventory;
import com.ddd.modular.inventory.domain.repository.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 库存应用服务（公开方法供其他模块通过 application.api 端口调用）。
 *
 * <p><b>跨模块调用约定</b>：
 * <ul>
 *   <li>订单模块不应直接注入本类，而应注入 {@code com.ddd.modular.inventory.application.api.InventoryPort}</li>
 *   <li>InventoryPort 接口由 common 模块定义，实现类在本模块（{@code InventoryPortAdapter}）</li>
 *   <li>事件订阅实际工程应使用 common 包内的领域事件基类，避免模块反向依赖</li>
 * </ul>
 */
@Service
public class InventoryAppService {

    private final InventoryRepository repository;

    public InventoryAppService(InventoryRepository repository) {
        this.repository = repository;
    }

    /** 初始化商品库存。 */
    @Transactional
    public void initialize(Long productId, int quantity) {
        if (repository.findByProductId(productId).isPresent()) {
            throw new BusinessException("INVENTORY_EXISTS", "库存已存在：" + productId);
        }
        repository.save(new Inventory(productId, quantity));
    }

    /** 主动预留（其他模块通过 InventoryPort 调用）。 */
    @Transactional
    public void reserve(Long productId, int quantity) {
        Inventory inv = repository.findByProductId(productId)
            .orElseThrow(() -> new BusinessException.InsufficientInventoryException(productId));
        inv.reserve(quantity);
        repository.save(inv);
    }

    /** 主动扣减（订单付款确认后调用）。 */
    @Transactional
    public void commit(Long productId, int quantity) {
        Inventory inv = repository.findByProductId(productId)
            .orElseThrow(() -> new BusinessException.InsufficientInventoryException(productId));
        inv.commit(quantity);
        repository.save(inv);
    }

    /** 主动释放（订单取消时调用）。 */
    @Transactional
    public void release(Long productId, int quantity) {
        Inventory inv = repository.findByProductId(productId)
            .orElseThrow(() -> new BusinessException.InsufficientInventoryException(productId));
        inv.release(quantity);
        repository.save(inv);
    }

    /** 刷新时间戳（领域行为完成后调用）。 */
    @SuppressWarnings("unused")
    private Instant now() { return Instant.now(); }
}