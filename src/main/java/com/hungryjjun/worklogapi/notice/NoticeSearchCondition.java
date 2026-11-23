package com.hungryjjun.worklogapi.notice;

import java.time.LocalDate;

/**
 * 목록 SQL(NoticeMapper.xml의 noticeSearchWhere)에 넘기는 검색 조건.
 * MyBatis는 record의 구성요소만 읽을 수 있으므로, SQL에 필요한 값은 여기서 모두 계산해 구성요소로 둔다.
 */
public record NoticeSearchCondition(
		LocalDate from,
		// to 다음 날 0시. create_at < toExclusive 로 비교해 to 날짜를 끝까지 포함한다.
		LocalDate toExclusive,
		String category,
		String searchType,
		// LIKE 특수문자를 이스케이프한 검색어. SQL에서 ESCAPE '!'와 함께 쓴다.
		String keywordPattern
) {

	// NoticeMapper.xml의 ESCAPE '!'와 같은 문자여야 한다.
	static final String LIKE_ESCAPE = "!";

	public static NoticeSearchCondition of(NoticeSearchRequest request) {
		LocalDate toExclusive = (request.to() == null) ? null : request.to().plusDays(1);
		String keywordPattern = (request.keyword() == null) ? null : escapeLike(request.keyword());

		return new NoticeSearchCondition(
				request.from(), toExclusive, request.category(), request.searchType(), keywordPattern);
	}

	// %, _ 를 일반 글자로 검색하도록 앞에 !를 붙인다.
	// 이스케이프 문자 ! 자체를 먼저 바꿔야, 뒤에서 붙인 !가 다시 치환되지 않는다.
	static String escapeLike(String keyword) {
		return keyword
				.replace(LIKE_ESCAPE, LIKE_ESCAPE + LIKE_ESCAPE)
				.replace("%", LIKE_ESCAPE + "%")
				.replace("_", LIKE_ESCAPE + "_");
	}
}
