package com.hungryjjun.worklogapi.notice;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import com.hungryjjun.worklogapi.common.validation.AllowedValues;
import com.hungryjjun.worklogapi.common.validation.DateRange;
import com.hungryjjun.worklogapi.common.validation.ValidPeriod;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// GET /api/notices 의 쿼리 파라미터 (페이징 + 검색 조건, 모두 선택 사항)
// 검증은 compact 생성자가 값을 정리한 뒤에 실행된다.
@ValidPeriod
public record NoticeSearchRequest(
		@Min(value = 1, message = "페이지는 1 이상이어야 합니다.")
		Integer page,

		@AllowedValues(values = { 20, 50, 100 }, message = "페이지 크기는 20, 50, 100 중 하나여야 합니다.")
		Integer size,

		// 등록일 기준 시작일 (yyyy-MM-dd). 형식이 틀리면 바인딩 실패로 고정 문구 400이 된다.
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
		LocalDate from,

		// 등록일 기준 종료일 (yyyy-MM-dd). 그날 끝까지 포함한다.
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
		LocalDate to,

		@Pattern(regexp = "GENERAL|IMPORTANT|HR", message = "구분은 GENERAL, IMPORTANT, HR 중 하나여야 합니다.")
		String category,

		@Pattern(regexp = "TITLE|CONTENT|ALL", message = "검색 유형은 TITLE, CONTENT, ALL 중 하나여야 합니다.")
		String searchType,

		@Size(max = 20, message = "검색어는 20자 이하로 입력해 주세요.")
		String keyword
) implements DateRange {

	public static final int DEFAULT_PAGE = 1;
	public static final int DEFAULT_SIZE = 20;
	public static final String DEFAULT_SEARCH_TYPE = "ALL";

	// compact 생성자: 빈 값을 정리하고 기본값을 채운다. 필드 대입은 컴파일러가 만든다.
	public NoticeSearchRequest {
		if (page == null) {
			page = DEFAULT_PAGE;
		}
		if (size == null) {
			size = DEFAULT_SIZE;
		}
		if (category != null && category.isBlank()) {
			category = null;
		}
		if (searchType == null || searchType.isBlank()) {
			searchType = DEFAULT_SEARCH_TYPE;
		}
		if (keyword != null) {
			keyword = keyword.strip();
			if (keyword.isEmpty()) {
				keyword = null;
			}
		}
	}

	// LIMIT ... OFFSET에 쓸 시작 위치. 큰 page 값에서 int 곱셈이 넘치지 않도록 long으로 계산한다.
	public long offset() {
		return (long) (page - 1) * size;
	}
}
