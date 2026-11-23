package com.hungryjjun.worklogapi.notice;

/**
 * AI가 질문에서 뽑아 돌려주는 검색 조건의 모양.
 * 모든 값을 String으로 받는다. enum으로 받으면 AI가 허용되지 않은 값을 줄 때 JSON 변환이 통째로 실패하지만,
 * String으로 받으면 서버가 값마다 검사해 틀린 것만 버릴 수 있다.
 */
public record AiSearchCondition(
		String keyword,
		String searchType,
		String category,
		String period
) {
}
