package com.hungryjjun.worklogapi.notice;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

// 요청 값을 SQL 조건으로 바꾸는 규칙만 확인한다. Spring, DB 없이 실행한다.
class NoticeSearchConditionTest {

	private static NoticeSearchRequest request(LocalDate from, LocalDate to, String category, String searchType, String keyword) {
		return new NoticeSearchRequest(1, 20, from, to, category, searchType, keyword);
	}

	@Test
	@DisplayName("C1-1 to는 그날 끝까지 포함하도록 다음 날 0시 미만(toExclusive) 조건으로 바꾼다")
	void toExclusiveIsNextDay() {
		NoticeSearchCondition cond = NoticeSearchCondition.of(request(null, LocalDate.of(2026, 11, 10), null, null, null));

		assertThat(cond.toExclusive()).isEqualTo(LocalDate.of(2026, 11, 11));
	}

	@Test
	@DisplayName("C1-2 연말 경계도 다음 날로 넘어간다 (12-31 → 다음 해 01-01)")
	void toExclusiveYearEnd() {
		NoticeSearchCondition cond = NoticeSearchCondition.of(request(null, LocalDate.of(2026, 12, 31), null, null, null));

		assertThat(cond.toExclusive()).isEqualTo(LocalDate.of(2027, 1, 1));
	}

	@Test
	@DisplayName("C1-3 to가 없으면 toExclusive도 없다")
	void noTo() {
		NoticeSearchCondition cond = NoticeSearchCondition.of(request(null, null, null, null, null));

		assertThat(cond.toExclusive()).isNull();
	}

	// CsvSource 값은 작은따옴표로 감싸야 앞뒤 공백·특수문자가 그대로 전달된다.
	@ParameterizedTest(name = "[{0}] → [{1}]")
	@CsvSource({
			"'100%',  '100!%'",
			"'a_b',   'a!_b'",
			"'wow!',  'wow!!'",
			"'!%_',   '!!!%!_'",
			"'C:\\temp', 'C:\\temp'"
	})
	@DisplayName("C2 LIKE 특수문자 %, _ 와 이스케이프 문자 ! 를 ! 로 이스케이프한다 (\\ 는 그대로)")
	void escapeKeyword(String keyword, String expected) {
		NoticeSearchCondition cond = NoticeSearchCondition.of(request(null, null, null, "ALL", keyword));

		assertThat(cond.keywordPattern()).isEqualTo(expected);
	}

	@Test
	@DisplayName("C3 특수문자가 없는 검색어는 그대로 둔다")
	void plainKeyword() {
		NoticeSearchCondition cond = NoticeSearchCondition.of(request(null, null, null, "ALL", "정보보안교육"));

		assertThat(cond.keywordPattern()).isEqualTo("정보보안교육");
	}

	@Test
	@DisplayName("C4 검색어가 없으면 keywordPattern도 없다")
	void noKeyword() {
		NoticeSearchCondition cond = NoticeSearchCondition.of(request(null, null, null, "ALL", null));

		assertThat(cond.keywordPattern()).isNull();
	}

	@Test
	@DisplayName("C5 from, category, searchType은 그대로 전달한다")
	void passThrough() {
		NoticeSearchCondition cond = NoticeSearchCondition.of(
				request(LocalDate.of(2026, 1, 1), null, "IMPORTANT", "CONTENT", null));

		assertThat(cond.from()).isEqualTo(LocalDate.of(2026, 1, 1));
		assertThat(cond.category()).isEqualTo("IMPORTANT");
		assertThat(cond.searchType()).isEqualTo("CONTENT");
	}
}
