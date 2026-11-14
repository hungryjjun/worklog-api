package com.hungryjjun.worklogapi.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PageResponseTest {

	@ParameterizedTest(name = "{0}건 / size {1} → {2}페이지")
	@CsvSource({
			"0,   20,  0",
			"1,   20,  1",
			"20,  20,  1",
			"21,  20,  2",
			"30,  20,  2",
			"40,  20,  2",
			"235, 20,  12",
			"235, 50,  5",
			"235, 100, 3",
	})
	@DisplayName("전체 페이지 수는 전체 건수를 size로 올림 나눗셈한 값이다")
	void totalPages(long totalCount, int size, int expected) {
		PageResponse<String> response = PageResponse.of(List.of(), totalCount, 1, size);

		assertThat(response.totalPages()).isEqualTo(expected);
	}

	@Test
	@DisplayName("of()는 전달한 값을 그대로 담는다")
	void keepsValues() {
		PageResponse<String> response = PageResponse.of(List.of("a", "b"), 235, 3, 20);

		assertThat(response.items()).containsExactly("a", "b");
		assertThat(response.totalCount()).isEqualTo(235);
		assertThat(response.page()).isEqualTo(3);
		assertThat(response.size()).isEqualTo(20);
	}

	@Test
	@DisplayName("전달한 목록을 나중에 바꿔도 응답의 items는 바뀌지 않는다")
	void itemsAreCopied() {
		List<String> source = new ArrayList<>(List.of("a"));
		PageResponse<String> response = PageResponse.of(source, 1, 1, 20);

		source.add("b");

		assertThat(response.items()).containsExactly("a");
		assertThatThrownBy(() -> response.items().add("c")).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	@DisplayName("size가 0 이하이면 IllegalArgumentException")
	void invalidSize() {
		assertThatThrownBy(() -> PageResponse.of(List.of(), 10, 1, 0))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
