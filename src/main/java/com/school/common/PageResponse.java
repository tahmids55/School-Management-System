package com.school.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Standard paginated API response wrapper.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static final int MAX_PAGE_SIZE = 200;

    public static <T> PageResponse<T> from(Page<T> springPage) {
        return new PageResponse<>(
                springPage.getContent(),
                springPage.getNumber(),
                springPage.getSize(),
                springPage.getTotalElements(),
                springPage.getTotalPages()
        );
    }

    public static <S, T> PageResponse<T> from(Page<S> springPage, Function<S, T> mapper) {
        List<T> mapped = springPage.getContent().stream().map(mapper).toList();
        return new PageResponse<>(
                mapped,
                springPage.getNumber(),
                springPage.getSize(),
                springPage.getTotalElements(),
                springPage.getTotalPages()
        );
    }
}
