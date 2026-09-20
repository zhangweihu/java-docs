package com.ddd.modular.inventory.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

@Data
@TableName("t_inventory")
public class InventoryPO {
    @TableId
    private Long productId;
    private Integer available;
    private Integer reserved;
    private Integer sold;
    private Instant updatedAt;
}