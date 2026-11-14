package com.hungryjjun.worklogapi.notice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.hungryjjun.worklogapi.common.ConflictException;
import com.hungryjjun.worklogapi.common.PageResponse;

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
	@DisplayName("목록 조회 파라미터 검증")
	class ListParams {

		@Test
		@DisplayName("P1 파라미터가 없으면 기본값으로 조회한다")
		void noParams() throws Exception {
			mockMvc.perform(get(URL))
					.andExpect(status().isOk());
		}

		@ParameterizedTest(name = "size={0}")
		@ValueSource(ints = { 20, 50, 100 })
		@DisplayName("P2 size는 20, 50, 100을 허용한다")
		void allowedSize(int size) throws Exception {
			mockMvc.perform(get(URL).param("page", "1").param("size", String.valueOf(size)))
					.andExpect(status().isOk());
		}

		@ParameterizedTest(name = "page={0}")
		@ValueSource(strings = { "0", "-1" })
		@DisplayName("P3 page가 1보다 작으면 400")
		void pageLessThanOne(String page) throws Exception {
			mockMvc.perform(get(URL).param("page", page))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errors", hasSize(1)))
					.andExpect(jsonPath("$.errors[0].field").value("page"))
					.andExpect(jsonPath("$.errors[0].message").value("페이지는 1 이상이어야 합니다."));
		}

		@ParameterizedTest(name = "size={0}")
		@ValueSource(strings = { "0", "10", "30", "200" })
		@DisplayName("P4 size가 허용값이 아니면 400")
		void sizeNotAllowed(String size) throws Exception {
			mockMvc.perform(get(URL).param("size", size))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errors", hasSize(1)))
					.andExpect(jsonPath("$.errors[0].field").value("size"))
					.andExpect(jsonPath("$.errors[0].message").value("페이지 크기는 20, 50, 100 중 하나여야 합니다."));
		}

		@Test
		@DisplayName("P5 page와 size가 모두 틀리면 오류 2건")
		void bothInvalid() throws Exception {
			mockMvc.perform(get(URL).param("page", "0").param("size", "30"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errors", hasSize(2)));
		}

		@ParameterizedTest(name = "{0}=abc")
		@ValueSource(strings = { "page", "size" })
		@DisplayName("P6 숫자가 아니면 400과 고정 문구, 내부 정보 없음")
		void notNumber(String param) throws Exception {
			mockMvc.perform(get(URL).param(param, "abc"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errors[0].field").value(param))
					.andExpect(jsonPath("$.errors[0].message").value("값의 형식이 올바르지 않습니다."))
					.andExpect(content().string(not(containsStringIgnoringCase("java"))))
					.andExpect(content().string(not(containsStringIgnoringCase("convert"))))
					.andExpect(content().string(not(containsStringIgnoringCase("exception"))));
		}
	}

	@Nested
	@DisplayName("목록 조회 응답")
	class ListResponse {

		private final NoticeSummary summary =
				new NoticeSummary(235L, "GENERAL", "제목", 3, "E001", LocalDateTime.of(2026, 11, 13, 9, 0));

		@Test
		@DisplayName("R1 파라미터가 없으면 page=1, size=20으로 조회하고 페이징 응답을 반환한다")
		void defaultPaging() throws Exception {
			given(noticeService.getNoticeList(any())).willReturn(PageResponse.of(List.of(summary), 235, 1, 20));

			mockMvc.perform(get(URL))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.items", hasSize(1)))
					.andExpect(jsonPath("$.items[0].noticeSeq").value(235))
					.andExpect(jsonPath("$.items[0].title").value("제목"))
					.andExpect(jsonPath("$.items[0].content").doesNotExist())
					.andExpect(jsonPath("$.totalCount").value(235))
					.andExpect(jsonPath("$.page").value(1))
					.andExpect(jsonPath("$.size").value(20))
					.andExpect(jsonPath("$.totalPages").value(12));

			ArgumentCaptor<NoticeSearchRequest> captor = ArgumentCaptor.forClass(NoticeSearchRequest.class);
			verify(noticeService).getNoticeList(captor.capture());
			assertThat(captor.getValue().page()).isEqualTo(1);
			assertThat(captor.getValue().size()).isEqualTo(20);
		}

		@Test
		@DisplayName("R2 전달한 page, size가 Service까지 그대로 전달된다")
		void passParams() throws Exception {
			given(noticeService.getNoticeList(any())).willReturn(PageResponse.of(List.of(), 235, 2, 50));

			mockMvc.perform(get(URL).param("page", "2").param("size", "50"))
					.andExpect(status().isOk());

			ArgumentCaptor<NoticeSearchRequest> captor = ArgumentCaptor.forClass(NoticeSearchRequest.class);
			verify(noticeService).getNoticeList(captor.capture());
			assertThat(captor.getValue().page()).isEqualTo(2);
			assertThat(captor.getValue().size()).isEqualTo(50);
		}
	}

	@Nested
	@DisplayName("중복 등록")
	class Conflict {

		@Test
		@DisplayName("#15 Service가 ConflictException을 던지면 409와 메시지를 반환한다")
		void duplicateNotice() throws Exception {
			given(noticeService.createNotice(any()))
					.willThrow(new ConflictException("같은 내용의 공지가 방금 등록되었습니다. 잠시 후 다시 시도해 주세요."));

			postJson(body("GENERAL", "제목", "내용", "E001"))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.status").value(409))
					.andExpect(jsonPath("$.message").value("같은 내용의 공지가 방금 등록되었습니다. 잠시 후 다시 시도해 주세요."))
					.andExpect(jsonPath("$.errors", hasSize(0)));
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
