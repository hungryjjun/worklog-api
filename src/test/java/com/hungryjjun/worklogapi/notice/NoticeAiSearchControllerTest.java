package com.hungryjjun.worklogapi.notice;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.hungryjjun.worklogapi.common.ExternalServiceException;
import com.hungryjjun.worklogapi.common.PageResponse;

// 웹 계층만 띄우고 NoticeAiSearchService는 mock으로 대체한다. Claude API와 키 환경변수가 필요 없다.
@WebMvcTest(NoticeAiSearchController.class)
class NoticeAiSearchControllerTest {

	private static final String URL = "/api/notices/ai-search";
	private static final String QUESTION = "올해 정보보안 교육 어디서 했어?";
	private static final String FAILURE_MESSAGE = "AI 응답을 받지 못했습니다. 잠시 후 다시 시도해 주세요.";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private NoticeAiSearchService noticeAiSearchService;

	private ResultActions postJson(String json) throws Exception {
		return mockMvc.perform(post(URL)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json));
	}

	private static String body(String question) {
		return """
				{"question": "%s"}
				""".formatted(question);
	}

	@Nested
	@DisplayName("정상 응답")
	class Success {

		@Test
		@DisplayName("A1 질문을 Service에 넘기고, 조건·목록·안내를 그대로 200으로 내려준다")
		void search() throws Exception {
			NoticeAiSearchResponse.Condition condition = new NoticeAiSearchResponse.Condition(
					"정보보안", "ALL", null, "THIS_YEAR", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
			NoticeSummary summary = new NoticeSummary(
					7L, "GENERAL", "정보보안교육 장소 안내", 3, "E001", LocalDateTime.of(2026, 6, 15, 9, 0));
			PageResponse<NoticeSummary> result = PageResponse.of(List.of(summary), 1, 1, 20);
			given(noticeAiSearchService.search(QUESTION))
					.willReturn(new NoticeAiSearchResponse(condition, result, null));

			postJson(body(QUESTION))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.condition.keyword").value("정보보안"))
					.andExpect(jsonPath("$.condition.searchType").value("ALL"))
					.andExpect(jsonPath("$.condition.category").doesNotExist())
					.andExpect(jsonPath("$.condition.period").value("THIS_YEAR"))
					.andExpect(jsonPath("$.condition.from").value("2026-01-01"))
					.andExpect(jsonPath("$.condition.to").value("2026-12-31"))
					.andExpect(jsonPath("$.result.totalCount").value(1))
					.andExpect(jsonPath("$.result.items[0].noticeSeq").value(7))
					.andExpect(jsonPath("$.message").doesNotExist());

			verify(noticeAiSearchService).search(QUESTION);
		}

		@Test
		@DisplayName("A2-1 question이 정확히 200자면 통과한다")
		void maxLength() throws Exception {
			given(noticeAiSearchService.search(anyString()))
					.willReturn(new NoticeAiSearchResponse(null, null, "검색 조건을 찾지 못했습니다."));

			postJson(body("가".repeat(200)))
					.andExpect(status().isOk());
		}
	}

	@Nested
	@DisplayName("입력값 검증 실패")
	class Validation {

		@ParameterizedTest(name = "[{index}] {0}")
		@ValueSource(strings = {
				"{}",
				"{\"question\": null}",
				"{\"question\": \"\"}",
				"{\"question\": \"   \"}"
		})
		@DisplayName("A2-2 question이 없거나 비어 있으면 400이고 Service(=AI)를 호출하지 않는다")
		void blankQuestion(String json) throws Exception {
			postJson(json)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.status").value(400))
					.andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."))
					.andExpect(jsonPath("$.errors[0].field").value("question"));

			verify(noticeAiSearchService, never()).search(anyString());
		}

		@Test
		@DisplayName("A2-3 question이 201자면 400이고 Service(=AI)를 호출하지 않는다")
		void tooLong() throws Exception {
			postJson(body("가".repeat(201)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errors[0].field").value("question"));

			verify(noticeAiSearchService, never()).search(anyString());
		}
	}

	@Nested
	@DisplayName("AI 호출 실패")
	class AiFailure {

		@Test
		@DisplayName("A3 AI 호출이 실패하면 502와 고정 문구를 반환하고, 외부 오류 메시지는 응답에 넣지 않는다")
		void badGateway() throws Exception {
			RuntimeException external = new RuntimeException("401 invalid x-api-key: authentication_error");
			given(noticeAiSearchService.search(QUESTION))
					.willThrow(new ExternalServiceException(FAILURE_MESSAGE, external));

			postJson(body(QUESTION))
					.andExpect(status().isBadGateway())
					.andExpect(jsonPath("$.status").value(502))
					.andExpect(jsonPath("$.message").value(FAILURE_MESSAGE))
					.andExpect(jsonPath("$.errors", hasSize(0)))
					.andExpect(content().string(not(containsString("x-api-key"))));
		}
	}
}
