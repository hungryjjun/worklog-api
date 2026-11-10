package com.hungryjjun.worklogapi.notice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record NoticeCreateRequest(
		@NotBlank(message = "구분은 필수입니다.")
		@Pattern(regexp = "GENERAL|IMPORTANT|HR", message = "구분은 GENERAL, IMPORTANT, HR 중 하나여야 합니다.")
		String category,

		@NotBlank(message = "제목은 필수입니다.")
		@Size(max = 200, message = "제목은 200자 이하로 입력해 주세요.")
		String title,

		@NotBlank(message = "내용은 필수입니다.")
		String content,

		@NotBlank(message = "등록자 사번은 필수입니다.")
		String createEmpNo
) {}
