package com.hungryjjun.worklogapi.notice;

import java.time.LocalDate;

import com.hungryjjun.worklogapi.common.PageResponse;

/**
 * AI 공지 검색 응답.
 * - condition: 검사를 통과해 실제 검색에 쓴 조건 (버린 값은 null)
 * - result: #8 목록 응답. 조건이 없어 검색하지 않았으면 null
 * - message: 안내 문구. 필요할 때만 값이 있다.
 */
public record NoticeAiSearchResponse(
		Condition condition,
		PageResponse<NoticeSummary> result,
		String message
) {

	public record Condition(
			String keyword,
			String searchType,
			String category,
			String period,
			LocalDate from,
			LocalDate to
	) {
	}
}
