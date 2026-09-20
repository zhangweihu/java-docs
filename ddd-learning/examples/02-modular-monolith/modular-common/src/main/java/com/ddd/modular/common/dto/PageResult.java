package com.ddd.modular.common.dto;

import java.util.List;

/** 通用分页结果 DTO。 */
public record PageResult<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public static <T> PageResult<T> of(List<T> content, int page, int size, long total) {
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResult<>(content, page, size, total, totalPages);
    }
}