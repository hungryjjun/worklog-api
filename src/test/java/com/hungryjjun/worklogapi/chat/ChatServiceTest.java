package com.hungryjjun.worklogapi.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import com.hungryjjun.worklogapi.common.ExternalServiceException;

// Spring 없이 Service만 테스트한다. 실제 Claude 대신 가짜 ChatModel을 넣는다.
class ChatServiceTest {

	private static final String QUESTION = "올해 정보보안교육 어디서 했어?";
	private static final String FAILURE_MESSAGE = "AI 응답을 받지 못했습니다. 잠시 후 다시 시도해 주세요.";

	/**
	 * Claude를 대신하는 가짜 모델.
	 * ChatClient의 prompt().user().call() 체이닝은 결국 ChatModel.call(Prompt) 하나로 모이므로,
	 * 이 메서드만 바꿔 끼우면 네트워크 없이 Service를 테스트할 수 있다.
	 */
	private static class FakeChatModel implements ChatModel {

		private ChatResponse response;
		private RuntimeException error;
		private Prompt lastPrompt;

		@Override
		public ChatResponse call(Prompt prompt) {
			this.lastPrompt = prompt;
			if (error != null) {
				throw error;
			}
			return response;
		}
	}

	private FakeChatModel chatModel;
	private ChatService chatService;

	@BeforeEach
	void setUp() {
		chatModel = new FakeChatModel();
		// 운영에서는 Spring이 자동설정한 ChatClient.Builder를 넣어 주고, 테스트에서는 가짜 모델로 만든 Builder를 넣는다.
		chatService = new ChatService(ChatClient.builder(chatModel));
	}

	private static ChatResponse responseOf(String text) {
		return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
	}

	@Test
	@DisplayName("S1 질문을 user 메시지로, 고정 지시문을 system 메시지로 보내고 모델의 답을 그대로 반환한다")
	void chat() {
		chatModel.response = responseOf("가짜 답변");

		String answer = chatService.chat(QUESTION);

		assertThat(answer).isEqualTo("가짜 답변");
		assertThat(chatModel.lastPrompt.getUserMessage().getText()).isEqualTo(QUESTION);
		assertThat(chatModel.lastPrompt.getSystemMessage().getText()).isEqualTo("한국어로 간결하게 답한다.");
	}

	@Test
	@DisplayName("S2 모델 호출이 예외로 실패하면 고정 문구의 ExternalServiceException으로 바꾸고 원인을 보관한다")
	void wrapFailure() {
		RuntimeException external = new RuntimeException("401 invalid x-api-key: authentication_error");
		chatModel.error = external;

		assertThatThrownBy(() -> chatService.chat(QUESTION))
				.isInstanceOf(ExternalServiceException.class)
				.hasMessage(FAILURE_MESSAGE)
				.hasCause(external);
	}

	@Test
	@DisplayName("S3 모델이 빈 답을 주면 ExternalServiceException을 던진다")
	void emptyAnswer() {
		chatModel.response = responseOf("");

		assertThatThrownBy(() -> chatService.chat(QUESTION))
				.isInstanceOf(ExternalServiceException.class)
				.hasMessage(FAILURE_MESSAGE);
	}

	@Test
	@DisplayName("S4 모델 응답에 결과가 없으면(content가 null) ExternalServiceException을 던진다")
	void noResult() {
		chatModel.response = new ChatResponse(List.of());

		assertThatThrownBy(() -> chatService.chat(QUESTION))
				.isInstanceOf(ExternalServiceException.class)
				.hasMessage(FAILURE_MESSAGE);
	}
}
