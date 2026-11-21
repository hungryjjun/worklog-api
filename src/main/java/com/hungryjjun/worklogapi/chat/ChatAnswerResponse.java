package com.hungryjjun.worklogapi.chat;

// Spring AI의 org.springframework.ai.chat.model.ChatResponse와 이름이 겹치지 않도록 ChatAnswerResponse로 한다.
public record ChatAnswerResponse(String answer) {
}
