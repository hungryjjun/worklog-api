package com.hungryjjun.worklogapi.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 입력 500자 제한은 형식 검증이면서 Claude API 입력 비용의 상한이기도 하다.
public record ChatRequest(
		@NotBlank(message = "질문을 입력해 주세요.")
		@Size(max = 500, message = "질문은 500자 이하로 입력해 주세요.")
		String message
) {
}
