package com.ddd.layered.ddd.domain.model;

import java.util.Objects;

/** 客户标识值对象。 */
public record CustomerId(Long value) {

    public CustomerId {
        Objects.requireNonNull(value, "CustomerId 不能为空");
        if (value <= 0) {
            throw new IllegalArgumentException("CustomerId 必须为正数");
        }
    }
}