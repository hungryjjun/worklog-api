package com.hungryjjun.worklogapi.notice;

// POST /api/notices/ai-search 요청 본문
// TODO(#9) 검증 어노테이션은 구현 단계에서 추가한다.
public record NoticeAiSearchRequest(
		String question
) {
}
