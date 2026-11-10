package com.hungryjjun.worklogapi.notice;

import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// 웹 계층(Controller, ControllerAdvice)만 띄우고 Service는 mock으로 대체한다. DB가 필요 없다.
@WebMvcTest(NoticeController.class)
class NoticeControllerTest {

	private static final String URL = "/api/notices";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private NoticeService noticeService;

	private ResultActions postJson(String json) throws Exception {
		return mockMvc.perform(post(URL)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json));
	}

	private static String body(String category, String title, String content, String createEmpNo) {
		return """
				{"category": "%s", "title": "%s", "content": "%s", "createEmpNo": "%s"}
				""".formatted(category, title, content, createEmpNo);
	}

	private void assertServiceNotCalled() {
		verify(noticeService, never()).createNotice(any());
	}

	@Nested
	@DisplayName("정상 등록")
	class Success {

		@Test
		@DisplayName("#1 모든 값이 올바르면 201과 noticeSeq를 반환한다")
		void createNotice() throws Exception {
			given(noticeService.createNotice(any())).willReturn(1L);

			postJson(body("GENERAL", "제목", "내용", "E001"))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.noticeSeq").value(1));
		}

		@Test
		@DisplayName("#5 제목이 정확히 200자면 통과한다")
		void titleMaxLength() throws Exception {
			given(noticeService.createNotice(any())).willReturn(1L);

			postJson(body("GENERAL", "가".repeat(200), "내용", "E001"))
					.andExpect(status().isCreated());
		}
	}

	@Nested
	@DisplayName("입력값 검증 실패")
	class Validation {

		@Test
		@DisplayName("#2 모든 필드가 없으면 4건의 오류를 반환한다")
		void allFieldsMissing() throws Exception {
			postJson("{}")
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.status").value(400))
					.andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."))
					.andExpect(jsonPath("$.errors", hasSize(4)))
					.andExpect(jsonPath("$.errors[*].message", hasItem("구분은 필수입니다.")))
					.andExpect(jsonPath("$.errors[*].message", hasItem("제목은 필수입니다.")))
					.andExpect(jsonPath("$.errors[*].message", hasItem("내용은 필수입니다.")))
					.andExpect(jsonPath("$.errors[*].message", hasItem("등록자 사번은 필수입니다.")));

			assertServiceNotCalled();
		}

		@Test
		@DisplayName("#3 제목이 빈 문자열이면 필수 오류")
		void titleEmpty() throws Exception {
			postJson(body("GENERAL", "", "내용", "E001"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errors", hasSize(1)))
					.andExpect(jsonPath("$.errors[0].field").value("title"))
					.andExpect(jsonPath("$.errors[0].message").value("제목은 필수입니다."));

			assertServiceNotCalled();
		}

		@Test
		@DisplayName("#4 제목이 공백만 있으면 필수 오류")
		void titleBlank() throws Exception {
			postJson(body("GENERAL", "   ", "내용", "E001"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errors", hasSize(1)))
					.andExpect(jsonPath("$.errors[0].field").value("title"))
					.andExpect(jsonPath("$.errors[0].message").value("제목은 필수입니다."));

			assertServiceNotCalled();
		}

		@Test
		@DisplayName("#6 제목이 201자면 길이 오류")
		void titleTooLong() throws Exception {
			postJson(body("GENERAL", "가".repeat(201), "내용", "E001"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errors", hasSize(1)))
					.andExpect(jsonPath("$.errors[0].field").value("title"))
					.andExpect(jsonPath("$.errors[0].message").value("제목은 200자 이하로 입력해 주세요."));

			assertServiceNotCalled();
		}

		@Test
		@DisplayName("#7 허용되지 않은 구분이면 형식 오류")
		void categoryNotAllowed() throws Exception {
			assertCategoryPatternError("ETC");
		}

		@Test
		@DisplayName("#8 구분은 대소문자를 구분한다")
		void categoryLowerCase() throws Exception {
			assertCategoryPatternError("general");
		}

		@Test
		@DisplayName("#9 구분은 전체가 일치해야 한다")
		void categoryPartialMatch() throws Exception {
			assertCategoryPatternError("GENERALX");
		}

		@Test
		@DisplayName("#10 구분이 빈 문자열이면 필수 오류와 형식 오류가 모두 나온다")
		void categoryEmpty() throws Exception {
			postJson(body("", "제목", "내용", "E001"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errors", hasSize(2)))
					.andExpect(jsonPath("$.errors[?(@.field == 'category')]", hasSize(2)))
					.andExpect(jsonPath("$.errors[*].message", hasItem("구분은 필수입니다.")))
					.andExpect(jsonPath("$.errors[*].message", hasItem("구분은 GENERAL, IMPORTANT, HR 중 하나여야 합니다.")));

			assertServiceNotCalled();
		}

		private void assertCategoryPatternError(String category) throws Exception {
			postJson(body(category, "제목", "내용", "E001"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errors", hasSize(1)))
					.andExpect(jsonPath("$.errors[0].field").value("category"))
					.andExpect(jsonPath("$.errors[0].message").value("구분은 GENERAL, IMPORTANT, HR 중 하나여야 합니다."));

			assertServiceNotCalled();
		}
	}

	@Nested
	@DisplayName("요청 본문 해석 실패")
	class NotReadable {

		@Test
		@DisplayName("#11 JSON 문법이 깨지면 400과 고정 메시지를 반환한다")
		void malformedJson() throws Exception {
			assertNotReadable(postJson("{\"title\": \"제목\""));
		}

		@Test
		@DisplayName("#12 본문이 없으면 400")
		void emptyBody() throws Exception {
			assertNotReadable(mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)));
		}

		@Test
		@DisplayName("#13 문자열 필드에 객체가 오면 400")
		void typeMismatch() throws Exception {
			assertNotReadable(postJson("""
					{"category": "GENERAL", "title": {"a": 1}, "content": "내용", "createEmpNo": "E001"}
					"""));
		}

		@Test
		@DisplayName("#14 응답에 파서·예외 클래스명이나 위치 정보가 들어가지 않는다")
		void noInternalInfo() throws Exception {
			postJson("{\"title\": \"제목\"")
					.andExpect(status().isBadRequest())
					.andExpect(content().string(not(containsStringIgnoringCase("jackson"))))
					.andExpect(content().string(not(containsStringIgnoringCase("exception"))))
					.andExpect(content().string(not(containsStringIgnoringCase("line"))))
					.andExpect(content().string(not(containsStringIgnoringCase("column"))))
					.andExpect(content().string(not(containsStringIgnoringCase("com.hungryjjun"))));
		}

		private void assertNotReadable(ResultActions result) throws Exception {
			result
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.status").value(400))
					.andExpect(jsonPath("$.message").value("요청 본문의 형식이 올바르지 않습니다."))
					.andExpect(jsonPath("$.errors", hasSize(0)));

			assertServiceNotCalled();
		}
	}
}
