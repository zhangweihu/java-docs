package com.ddd.modular.payment.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@TableName("t_account")
public class AccountPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private BigDecimal balance;
    private BigDecimal frozen;
    private String currency;
    private Instant updatedAt;
}