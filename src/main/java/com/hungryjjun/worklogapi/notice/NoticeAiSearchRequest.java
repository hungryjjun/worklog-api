package com.hungryjjun.worklogapi.notice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// POST /api/notices/ai-search 요청 본문
// 검증을 통과해야 AI를 호출하므로, 잘못된 요청에는 비용이 들지 않는다.
public record NoticeAiSearchRequest(
		@NotBlank(message = "질문을 입력해 주세요.")
		@Size(max = 200, message = "질문은 200자 이하로 입력해 주세요.")
		String question
) {
}
