package com.hungryjjun.worklogapi.common;

import java.util.List;

// 목록 API의 공통 페이징 응답
public record PageResponse<T>(
		List<T> items,
		long totalCount,
		int page,
		int size,
		int totalPages
) {

	public PageResponse {
		items = List.copyOf(items);
	}

	public static <T> PageResponse<T> of(List<T> items, long totalCount, int page, int size) {
		return new PageResponse<>(items, totalCount, page, size, calculateTotalPages(totalCount, size));
	}

	// 올림 나눗셈. 0건이면 0페이지가 된다.
	static int calculateTotalPages(long totalCount, int size) {
		if (size <= 0) {
			throw new IllegalArgumentException("size는 1 이상이어야 합니다: " + size);
		}
		return Math.toIntExact(Math.ceilDiv(totalCount, size));
	}
}
