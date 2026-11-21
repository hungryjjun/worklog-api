package com.hungryjjun.worklogapi.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.hungryjjun.worklogapi.common.ExternalServiceException;

@Service
public class ChatService {

	// 매 호출에 함께 보내는 지시문. 출력 상한(500토큰) 안에서 답이 잘리지 않도록 간결하게 답하게 한다.
	static final String SYSTEM_PROMPT = "한국어로 간결하게 답한다.";

	static final String FAILURE_MESSAGE = "AI 응답을 받지 못했습니다. 잠시 후 다시 시도해 주세요.";

	private final ChatClient chatClient;

	// ChatClient.Builder는 Spring AI 자동설정이 application.properties의 모델·키·토큰 설정을 담아 만들어 준다.
	public ChatService(ChatClient.Builder chatClientBuilder) {
		this.chatClient = chatClientBuilder
				.defaultSystem(SYSTEM_PROMPT)
				.build();
	}

	public String chat(String message) {
		String answer;
		try {
			answer = chatClient.prompt()
					.user(message)
					.call()
					.content();
		} catch (RuntimeException e) {
			// 키 오류, 시간 초과, 크레딧 소진 등. Spring AI·Anthropic SDK의 예외 종류가 다양하므로 외부 호출 경계에서 모두 감싼다.
			throw new ExternalServiceException(FAILURE_MESSAGE, e);
		}

		if (answer == null || answer.isBlank()) {
			throw new ExternalServiceException(FAILURE_MESSAGE, new IllegalStateException("AI 응답 본문이 비어 있습니다."));
		}
		return answer;
	}
}
