package com.hungryjjun.worklogapi.notice;

import java.time.Clock;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import jakarta.validation.Validator;

@Service
public class NoticeAiSearchService {

	private final ChatClient chatClient;
	private final NoticeService noticeService;
	private final Validator validator;
	private final Clock clock;

	public NoticeAiSearchService(ChatClient.Builder chatClientBuilder, NoticeService noticeService,
			Validator validator, Clock clock) {
		this.chatClient = chatClientBuilder.build();
		this.noticeService = noticeService;
		this.validator = validator;
		this.clock = clock;
	}

	// TODO(#9) 구현 전 껍데기: 아무것도 하지 않고 빈 응답을 돌려준다.
	public NoticeAiSearchResponse search(String question) {
		return new NoticeAiSearchResponse(null, null, null);
	}
}
