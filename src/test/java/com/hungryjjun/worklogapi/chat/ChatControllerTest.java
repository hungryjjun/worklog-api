package com.hungryjjun.worklogapi.chat;

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

// 웹 계층만 띄우고 ChatService는 mock으로 대체한다. Claude API와 키 환경변수가 필요 없다.
@WebMvcTest(ChatController.class)
class ChatControllerTest {

	private static final String URL = "/api/chat";
	private static final String QUESTION = "올해 정보보안교육 어디서 했어?";
	private static final String FAILURE_MESSAGE = "AI 응답을 받지 못했습니다. 잠시 후 다시 시도해 주세요.";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ChatService chatService;

	private ResultActions postJson(String json) throws Exception {
		return mockMvc.perform(post(URL)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json));
	}

	private static String body(String message) {
		return """
				{"message": "%s"}
				""".formatted(message);
	}

	@Nested
	@DisplayName("정상 응답")
	class Success {

		@Test
		@DisplayName("C1 질문을 Service에 넘기고 200과 answer를 반환한다")
		void chat() throws Exception {
			// 이번 단계에는 공지 검색이 없으므로 답의 내용은 검증하지 않고, 받은 값을 그대로 내려주는지만 본다.
			given(chatService.chat(QUESTION)).willReturn("가짜 답변");

			postJson(body(QUESTION))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.answer").value("가짜 답변"));

			verify(chatService).chat(QUESTION);
		}

		@Test
		@DisplayName("C3-1 message가 정확히 500자면 통과한다")
		void maxLength() throws Exception {
			given(chatService.chat(anyString())).willReturn("가짜 답변");

			postJson(body("가".repeat(500)))
					.andExpect(status().isOk());
		}
	}

	@Nested
	@DisplayName("입력값 검증 실패")
	class Validation {

		@ParameterizedTest(name = "[{index}] {0}")
		@ValueSource(strings = {
				"{}",
				"{\"message\": null}",
				"{\"message\": \"\"}",
				"{\"message\": \"   \"}"
		})
		@DisplayName("C2 message가 없거나 비어 있으면 400이고 Service를 호출하지 않는다")
		void blankMessage(String json) throws Exception {
			postJson(json)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.status").value(400))
					.andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."))
					.andExpect(jsonPath("$.errors[0].field").value("message"));

			verify(chatService, never()).chat(anyString());
		}

		@Test
		@DisplayName("C3-2 message가 501자면 400이고 Service를 호출하지 않는다")
		void tooLong() throws Exception {
			postJson(body("가".repeat(501)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errors[0].field").value("message"));

			verify(chatService, never()).chat(anyString());
		}
	}

	@Nested
	@DisplayName("AI 호출 실패")
	class AiFailure {

		@Test
		@DisplayName("C4 AI 호출이 실패하면 502와 고정 문구를 반환하고, 외부 오류 메시지는 응답에 넣지 않는다")
		void badGateway() throws Exception {
			RuntimeException external = new RuntimeException("401 invalid x-api-key: authentication_error");
			given(chatService.chat(QUESTION)).willThrow(new ExternalServiceException(FAILURE_MESSAGE, external));

			postJson(body(QUESTION))
					.andExpect(status().isBadGateway())
					.andExpect(jsonPath("$.status").value(502))
					.andExpect(jsonPath("$.message").value(FAILURE_MESSAGE))
					.andExpect(jsonPath("$.errors", hasSize(0)))
					.andExpect(content().string(not(containsString("x-api-key"))))
					.andExpect(content().string(not(containsString("authentication_error"))));
		}
	}
}
