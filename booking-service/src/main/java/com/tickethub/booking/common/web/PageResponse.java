package com.tickethub.booking.common.web;

import java.util.List;
import java.util.function.Function;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.domain.Page;

/** Stable pagination envelope so Spring's Page internals are never exposed in the API. */
@Getter
@AllArgsConstructor
public class PageResponse<T> {
    private final List<T> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;

    public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
        List<T> items = page.getContent().stream().map(mapper).toList();
        return new PageResponse<>(items, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
