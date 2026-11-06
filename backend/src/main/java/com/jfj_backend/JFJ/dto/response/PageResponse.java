package com.jfj_backend.JFJ.dto.response;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * Our own page shape instead of returning Spring's Page directly: Spring's JSON for Page is not a
 * stable contract, and this way the frontend sees exactly these five fields. page is 0-based.
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

	public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
		return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(),
				page.getSize(), page.getTotalElements(), page.getTotalPages());
	}
}
