package com.hungryjjun.worklogapi.notice;

import com.hungryjjun.worklogapi.common.validation.AllowedValues;

import jakarta.validation.constraints.Min;

// GET /api/notices?page=1&size=20 의 쿼리 파라미터
public record NoticeSearchRequest(
		@Min(value = 1, message = "페이지는 1 이상이어야 합니다.")
		Integer page,

		@AllowedValues(values = { 20, 50, 100 }, message = "페이지 크기는 20, 50, 100 중 하나여야 합니다.")
		Integer size
) {

	public static final int DEFAULT_PAGE = 1;
	public static final int DEFAULT_SIZE = 20;

	// compact 생성자: 파라미터가 없으면 기본값을 채운다. 필드 대입은 컴파일러가 만든다.
	public NoticeSearchRequest {
		if (page == null) {
			page = DEFAULT_PAGE;
		}
		if (size == null) {
			size = DEFAULT_SIZE;
		}
	}

	// LIMIT ... OFFSET에 쓸 시작 위치. 큰 page 값에서 int 곱셈이 넘치지 않도록 long으로 계산한다.
	public long offset() {
		return (long) (page - 1) * size;
	}
}
