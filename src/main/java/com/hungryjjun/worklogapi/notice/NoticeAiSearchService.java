package com.hungryjjun.worklogapi.notice;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

import com.anthropic.models.messages.OutputConfig;
import com.hungryjjun.worklogapi.common.ExternalServiceException;
import com.hungryjjun.worklogapi.common.PageResponse;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

/**
 * 문장 질문 → AI가 검색 조건 추출 → 서버가 검사·기간 계산 → #8 목록 조회.
 * AI에는 질문만 보내고, 공지 데이터는 보내지 않는다. (본문으로 답을 만드는 RAG는 다음 단계)
 */
@Service
public class NoticeAiSearchService {

	private static final Logger log = LoggerFactory.getLogger(NoticeAiSearchService.class);

	// 날짜는 AI가 계산하지 않고 기간 "종류"만 고르게 한다. 실제 날짜는 서버가 한국 시간으로 계산한다.
	static final String SYSTEM_PROMPT = """
			너는 사내 공지사항 검색 조건을 뽑는 도우미다. 질문에 답하지 말고 아래 4개 값만 돌려준다.
			- keyword: 공지 제목이나 본문에서 찾을 핵심 단어 하나. 20자 이하. 없으면 null
			- searchType: 제목에서만 찾으라고 하면 TITLE, 본문에서만 찾으라고 하면 CONTENT, 그 외에는 ALL
			- category: 일반 공지면 GENERAL, 중요 공지면 IMPORTANT, 인사 공지면 HR. 말이 없으면 null
			- period: 오늘이면 TODAY, 올해면 THIS_YEAR, 작년이면 LAST_YEAR. 그 외에는 null
			날짜를 직접 계산하지 말고 period 값만 고른다.
			""";

	/*
	 * Haiku 5.5는 thinking을 지정하지 않으면 adaptive thinking이 켜지고, thinking 토큰도 출력 상한에 포함된다.
	 * 상한을 200으로 두었을 때 thinking이 상한을 다 써서 JSON 없이 끝나는 장애가 있었다(2026-11-23).
	 * effort를 LOW로 낮춰 thinking을 줄이고, 그래도 생각할 때를 대비해 상한에 여유를 둔다.
	 */
	static final int MAX_OUTPUT_TOKENS = 1024;
	static final OutputConfig.Effort EFFORT = OutputConfig.Effort.LOW;

	// 답 JSON을 record로 바꾸는 변환기. 형식 지시문(JSON 스키마)도 여기서 만든다.
	private static final BeanOutputConverter<AiSearchCondition> CONVERTER =
			new BeanOutputConverter<>(AiSearchCondition.class);

	static final String FAILURE_MESSAGE = "AI 응답을 받지 못했습니다. 잠시 후 다시 시도해 주세요.";
	static final String NO_CONDITION_MESSAGE = "검색 조건을 찾지 못했습니다.";
	static final String NO_RESULT_MESSAGE = "일치하는 검색결과가 없습니다.";

	private static final Set<String> PERIODS = Set.of("TODAY", "THIS_YEAR", "LAST_YEAR");

	// Anthropic 응답의 stop_reason 값. Spring AI가 finish reason에 그대로 넣는다.
	private static final String MAX_TOKENS_REASON = "max_tokens";

	private final ChatClient chatClient;
	private final NoticeService noticeService;
	private final Validator validator;
	private final Clock clock;

	public NoticeAiSearchService(ChatClient.Builder chatClientBuilder, NoticeService noticeService,
			Validator validator, Clock clock) {
		// entity()를 쓰지 않으므로 JSON 형식 지시문을 직접 붙인다. user 메시지에는 질문만 남는다.
		this.chatClient = chatClientBuilder
				.defaultSystem(SYSTEM_PROMPT + "\n" + CONVERTER.getFormat())
				.build();
		this.noticeService = noticeService;
		this.validator = validator;
		this.clock = clock;
	}

	public NoticeAiSearchResponse search(String question) {
		AiSearchCondition ai = askAi(question);

		NoticeSearchRequest checked = discardInvalid(ai);
		String period = checkPeriod(ai.period());

		// searchType은 검색어가 있어야 의미가 있으므로 조건으로 세지 않는다.
		if (checked.keyword() == null && checked.category() == null && period == null) {
			return new NoticeAiSearchResponse(null, null, NO_CONDITION_MESSAGE);
		}

		Period range = periodOf(period);
		NoticeSearchRequest request = new NoticeSearchRequest(
				null, null, range.from(), range.to(), checked.category(), checked.searchType(), checked.keyword());

		PageResponse<NoticeSummary> result = noticeService.getNoticeList(request);
		String message = (result.totalCount() == 0) ? NO_RESULT_MESSAGE : null;

		NoticeAiSearchResponse.Condition condition = new NoticeAiSearchResponse.Condition(
				request.keyword(), request.searchType(), request.category(), period, request.from(), request.to());
		return new NoticeAiSearchResponse(condition, result, message);
	}

	/*
	 * AI 호출.
	 * entity()/responseEntity()는 응답을 돌려주기 전에 JSON 변환부터 하므로, 변환이 실패하면 종료 이유(finish reason)를
	 * 볼 수 없다. 그래서 응답(ChatResponse)을 그대로 받아 종료 이유를 먼저 확인한 뒤 직접 변환한다.
	 * 실패 원인은 cause에 담고, 공통 처리기(GlobalExceptionHandler)가 log.error로 남긴다. 사용자 응답은 고정 문구다.
	 */
	private AiSearchCondition askAi(String question) {
		ChatResponse response;
		try {
			response = chatClient.prompt()
					.user(question)
					.options(AnthropicChatOptions.builder()
							.maxTokens(MAX_OUTPUT_TOKENS)
							.effort(EFFORT))
					.call()
					.chatResponse();
		} catch (RuntimeException e) {
			// 키 오류, 시간 초과, 크레딧 소진 등
			throw new ExternalServiceException(FAILURE_MESSAGE, e);
		}

		Generation result = (response == null) ? null : response.getResult();
		String text = (result == null) ? null : result.getOutput().getText();
		String finishReason = (result == null) ? null : result.getMetadata().getFinishReason();

		if (text == null || text.isBlank()) {
			String reason = MAX_TOKENS_REASON.equals(finishReason)
					? "AI 응답이 출력 상한(max_tokens=" + MAX_OUTPUT_TOKENS + ")에 걸려 본문이 비었습니다."
					: "AI 응답 본문이 비어 있습니다. finishReason=" + finishReason;
			throw new ExternalServiceException(FAILURE_MESSAGE, new IllegalStateException(reason));
		}

		AiSearchCondition ai;
		try {
			ai = CONVERTER.convert(text);
		} catch (RuntimeException e) {
			// JSON이 아닌 글이거나, 상한에 걸려 JSON이 중간에 끊긴 경우. 종료 이유를 함께 남겨 둘을 구분한다.
			throw new ExternalServiceException(FAILURE_MESSAGE,
					new IllegalStateException("AI 응답을 검색 조건으로 변환하지 못했습니다. finishReason=" + finishReason, e));
		}
		if (ai == null) {
			throw new ExternalServiceException(FAILURE_MESSAGE, new IllegalStateException("AI 응답 본문이 비어 있습니다."));
		}
		return ai;
	}

	/**
	 * #8 목록 API와 같은 규칙(NoticeSearchRequest의 어노테이션·정규화)으로 검사하고, 틀린 값만 버린다.
	 * 사용자가 직접 입력한 값이 아니라 고칠 방법이 없으므로 400 대신 그 조건만 빼고 검색한다.
	 */
	private NoticeSearchRequest discardInvalid(AiSearchCondition ai) {
		// compact 생성자가 앞뒤 공백 제거, 빈 값 → null, searchType 기본값(ALL)을 먼저 처리한다.
		NoticeSearchRequest candidate = new NoticeSearchRequest(
				null, null, null, null, ai.category(), ai.searchType(), ai.keyword());

		Set<String> rejected = new HashSet<>();
		for (ConstraintViolation<NoticeSearchRequest> violation : validator.validate(candidate)) {
			String field = violation.getPropertyPath().toString();
			rejected.add(field);
			log.warn("AI 검색 조건을 버림: field={}, value={}, reason={}",
					field, violation.getInvalidValue(), violation.getMessage());
		}

		// 버린 searchType은 null로 넘겨 compact 생성자가 다시 ALL로 채우게 한다.
		return new NoticeSearchRequest(null, null, null, null,
				rejected.contains("category") ? null : candidate.category(),
				rejected.contains("searchType") ? null : candidate.searchType(),
				rejected.contains("keyword") ? null : candidate.keyword());
	}

	private String checkPeriod(String period) {
		if (period == null || period.isBlank()) {
			return null;
		}
		if (!PERIODS.contains(period)) {
			log.warn("AI 검색 조건을 버림: field=period, value={}", period);
			return null;
		}
		return period;
	}

	// 기간 종류를 실제 날짜로 바꾼다. "오늘"은 주입받은 시계(한국 시간) 기준이다.
	private Period periodOf(String period) {
		if (period == null) {
			return new Period(null, null);
		}
		LocalDate today = LocalDate.now(clock);
		return switch (period) {
			case "TODAY" -> new Period(today, today);
			case "THIS_YEAR" -> Period.ofYear(today.getYear());
			case "LAST_YEAR" -> Period.ofYear(today.getYear() - 1);
			default -> throw new IllegalStateException("검사되지 않은 기간 값: " + period);
		};
	}

	private record Period(LocalDate from, LocalDate to) {

		static Period ofYear(int year) {
			return new Period(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
		}
	}
}
