package com.jobtrack.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Envelope for every paginated list response.
 *
 * <p>Exposes the metadata a data-table needs (current page, size, total elements, total pages) so
 * the client never has to fetch the whole table to paginate.</p>
 *
 * @param content      page items already mapped to their response DTO
 * @param page         zero-based page index
 * @param size         requested page size
 * @param totalElements total rows matching the filter
 * @param totalPages   total pages matching the filter
 * @param first        true when this is the first page
 * @param last         true when this is the last page
 * @param empty        true when the page has no items
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        boolean empty
) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.isEmpty());
    }

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageResponse<>(
                content,
                page,
                size,
                totalElements,
                totalPages,
                page == 0,
                totalPages == 0 || page >= totalPages - 1,
                content.isEmpty());
    }
}
