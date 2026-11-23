package com.hungryjjun.worklogapi.notice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import com.anthropic.models.messages.OutputConfig;

import com.hungryjjun.worklogapi.common.ExternalServiceException;
import com.hungryjjun.worklogapi.common.PageResponse;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

// Spring 없이 Service만 테스트한다. 실제 Claude 대신 가짜 ChatModel을, NoticeService 대신 mock을 넣는다.
class NoticeAiSearchServiceTest {

	private static final String QUESTION = "올해 정보보안 교육 어디서 했어?";
	private static final String FAILURE_MESSAGE = "AI 응답을 받지 못했습니다. 잠시 후 다시 시도해 주세요.";
	private static final String NO_CONDITION_MESSAGE = "검색 조건을 찾지 못했습니다.";
	private static final String NO_RESULT_MESSAGE = "일치하는 검색결과가 없습니다.";

	/**
	 * 테스트의 "지금". UTC로는 2025-12-31 15:30이지만 서울 시간으로는 2026-01-01 00:30이다.
	 * 해가 바뀌는 순간이라, 서버가 UTC로 계산하면 TODAY·THIS_YEAR·LAST_YEAR가 모두 1년(1일) 어긋나 테스트가 실패한다.
	 * 실행하는 날짜와 PC 시간대에 상관없이 늘 같은 결과가 나온다.
	 */
	private static final Clock FIXED_CLOCK =
			Clock.fixed(Instant.parse("2025-12-31T15:30:00Z"), ZoneId.of("Asia/Seoul"));

	// ChatServiceTest와 같은 방식의 가짜 모델. 정해 둔 문자열(JSON)을 AI 답처럼 돌려준다.
	private static class FakeChatModel implements ChatModel {

		private String text;
		// 응답 종료 이유. 실제 Anthropic 응답의 stop_reason 문자열과 같은 값(end_turn, max_tokens ...)을 넣는다.
		private String finishReason = "end_turn";
		// true면 실제 Haiku 5.5처럼 text 앞에 내용이 생략된(빈) thinking 블록 Generation을 하나 더 붙인다.
		private boolean withThinking;
		private RuntimeException error;
		private Prompt lastPrompt;

		@Override
		public ChatResponse call(Prompt prompt) {
			this.lastPrompt = prompt;
			if (error != null) {
				throw error;
			}
			ChatGenerationMetadata metadata = ChatGenerationMetadata.builder().finishReason(finishReason).build();
			// AnthropicChatModel은 text 블록을 모아 항상 첫 번째 Generation에 넣고, thinking 블록은 그 뒤에 둔다.
			Generation answer = new Generation(new AssistantMessage(text), metadata);
			if (!withThinking) {
				return new ChatResponse(List.of(answer));
			}
			Generation thinking = new Generation(AssistantMessage.builder().content("").build(), metadata);
			return new ChatResponse(List.of(answer, thinking));
		}

		// ChatClient는 모델의 기본 옵션 위에 호출별 옵션을 합친다. 운영(AnthropicChatModel)과 같은 타입을 돌려줘야
		// effort 같은 Anthropic 전용 옵션이 Prompt에 남는지 확인할 수 있다.
		@Override
		public ChatOptions getOptions() {
			return AnthropicChatOptions.builder().build();
		}
	}

	private FakeChatModel chatModel;
	private NoticeService noticeService;
	private NoticeAiSearchService service;

	@BeforeEach
	void setUp() {
		chatModel = new FakeChatModel();
		noticeService = mock(NoticeService.class);
		// #8과 같은 규칙으로 검사하는지 보려면 진짜 Validator가 필요하다.
		Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
		service = new NoticeAiSearchService(ChatClient.builder(chatModel), noticeService, validator, FIXED_CLOCK);

		// 기본: 검색하면 1건이 나온다. 0건 테스트는 따로 바꿔 끼운다.
		given(noticeService.getNoticeList(any())).willReturn(pageOf(1));
	}

	// AI가 돌려줄 JSON. null은 JSON null로 넣는다.
	private void aiReturns(String keyword, String searchType, String category, String period) {
		chatModel.text = """
				{"keyword": %s, "searchType": %s, "category": %s, "period": %s}
				""".formatted(json(keyword), json(searchType), json(category), json(period));
	}

	private static String json(String value) {
		return (value == null) ? "null" : "\"" + value + "\"";
	}

	private static PageResponse<NoticeSummary> pageOf(int count) {
		List<NoticeSummary> items = (count == 0)
				? List.of()
				: List.of(new NoticeSummary(7L, "GENERAL", "정보보안교육 장소 안내", 3, "E001",
						LocalDateTime.of(2026, 6, 15, 9, 0)));
		return PageResponse.of(items, count, 1, 20);
	}

	// NoticeService에 실제로 넘어간 검색 요청을 꺼낸다.
	private NoticeSearchRequest searchedRequest() {
		ArgumentCaptor<NoticeSearchRequest> captor = ArgumentCaptor.forClass(NoticeSearchRequest.class);
		verify(noticeService).getNoticeList(captor.capture());
		return captor.getValue();
	}

	@Nested
	@DisplayName("기간 계산 (고정 시계: 서울 2026-01-01 00:30)")
	class Period {

		@Test
		@DisplayName("D1 TODAY는 오늘 ~ 오늘 (UTC 날짜 2025-12-31이 아니라 서울 날짜)")
		void today() {
			aiReturns("정보보안", null, null, "TODAY");

			NoticeAiSearchResponse response = service.search(QUESTION);

			NoticeSearchRequest request = searchedRequest();
			assertThat(request.from()).isEqualTo(LocalDate.of(2026, 1, 1));
			assertThat(request.to()).isEqualTo(LocalDate.of(2026, 1, 1));
			assertThat(response.condition().period()).isEqualTo("TODAY");
			assertThat(response.condition().from()).isEqualTo(LocalDate.of(2026, 1, 1));
			assertThat(response.condition().to()).isEqualTo(LocalDate.of(2026, 1, 1));
		}

		@Test
		@DisplayName("D2 THIS_YEAR는 올해 1월 1일 ~ 12월 31일")
		void thisYear() {
			aiReturns("정보보안", null, null, "THIS_YEAR");

			service.search(QUESTION);

			NoticeSearchRequest request = searchedRequest();
			assertThat(request.from()).isEqualTo(LocalDate.of(2026, 1, 1));
			assertThat(request.to()).isEqualTo(LocalDate.of(2026, 12, 31));
		}

		@Test
		@DisplayName("D3 LAST_YEAR는 작년 1월 1일 ~ 12월 31일")
		void lastYear() {
			aiReturns("정보보안", null, null, "LAST_YEAR");

			service.search(QUESTION);

			NoticeSearchRequest request = searchedRequest();
			assertThat(request.from()).isEqualTo(LocalDate.of(2025, 1, 1));
			assertThat(request.to()).isEqualTo(LocalDate.of(2025, 12, 31));
		}

		@Test
		@DisplayName("D4 period가 없으면 기간 조건 없이 검색한다")
		void noPeriod() {
			aiReturns("정보보안", null, null, null);

			NoticeAiSearchResponse response = service.search(QUESTION);

			NoticeSearchRequest request = searchedRequest();
			assertThat(request.from()).isNull();
			assertThat(request.to()).isNull();
			assertThat(response.condition().period()).isNull();
		}
	}

	@Nested
	@DisplayName("AI 답 검사: 틀린 값은 400 대신 그 조건만 버린다")
	class Sanitize {

		@Test
		@DisplayName("V1 허용되지 않은 category(NOTICE)는 버리고 나머지 조건으로 검색한다")
		void invalidCategory() {
			aiReturns("정보보안", "TITLE", "NOTICE", null);

			NoticeAiSearchResponse response = service.search(QUESTION);

			NoticeSearchRequest request = searchedRequest();
			assertThat(request.category()).isNull();
			assertThat(request.keyword()).isEqualTo("정보보안");
			assertThat(request.searchType()).isEqualTo("TITLE");
			assertThat(response.condition().category()).isNull();
		}

		@Test
		@DisplayName("V2 허용되지 않은 searchType(SUBJECT)은 버리고 기본값 ALL로 검색한다")
		void invalidSearchType() {
			aiReturns("정보보안", "SUBJECT", null, null);
			service.search(QUESTION);
			assertThat(searchedRequest().searchType()).isEqualTo("ALL");
		}

		@Test
		@DisplayName("V2-2 소문자 title도 #8 규칙처럼 허용하지 않는다")
		void lowerCaseSearchType() {
			aiReturns("정보보안", "title", null, null);
			service.search(QUESTION);
			assertThat(searchedRequest().searchType()).isEqualTo("ALL");
		}

		@Test
		@DisplayName("V3 20자를 넘는 keyword는 버리고, 남은 category로 검색한다")
		void tooLongKeyword() {
			aiReturns("가".repeat(21), null, "HR", null);

			NoticeAiSearchResponse response = service.search(QUESTION);

			NoticeSearchRequest request = searchedRequest();
			assertThat(request.keyword()).isNull();
			assertThat(request.category()).isEqualTo("HR");
			assertThat(response.condition().keyword()).isNull();
		}

		@Test
		@DisplayName("V3-2 keyword 앞뒤 공백은 #8처럼 제거하고 검색한다")
		void stripKeyword() {
			aiReturns("  정보보안  ", null, null, null);
			service.search(QUESTION);
			assertThat(searchedRequest().keyword()).isEqualTo("정보보안");
		}

		@Test
		@DisplayName("V4 허용되지 않은 period(NEXT_YEAR)는 버리고 기간 없이 검색한다")
		void invalidPeriod() {
			aiReturns("정보보안", null, null, "NEXT_YEAR");

			NoticeAiSearchResponse response = service.search(QUESTION);

			NoticeSearchRequest request = searchedRequest();
			assertThat(request.from()).isNull();
			assertThat(request.to()).isNull();
			assertThat(response.condition().period()).isNull();
		}
	}

	@Nested
	@DisplayName("조건 없음")
	class NoCondition {

		@Test
		@DisplayName("V5 모든 값이 틀리면 검색하지 않고 안내 문구를 준다 (result는 null)")
		void allInvalid() {
			aiReturns("가".repeat(21), "SUBJECT", "NOTICE", "NEXT_YEAR");

			NoticeAiSearchResponse response = service.search(QUESTION);

			verify(noticeService, never()).getNoticeList(any());
			assertThat(response.result()).isNull();
			assertThat(response.message()).isEqualTo(NO_CONDITION_MESSAGE);
		}

		@Test
		@DisplayName("V5-2 AI가 모든 값을 null로 주면 검색하지 않는다")
		void allNull() {
			aiReturns(null, null, null, null);

			NoticeAiSearchResponse response = service.search(QUESTION);

			verify(noticeService, never()).getNoticeList(any());
			assertThat(response.result()).isNull();
			assertThat(response.message()).isEqualTo(NO_CONDITION_MESSAGE);
		}

		@Test
		@DisplayName("V6 searchType만 있으면 검색어가 없어 의미가 없으므로 조건 없음으로 본다")
		void onlySearchType() {
			aiReturns("   ", "TITLE", "", null);

			NoticeAiSearchResponse response = service.search(QUESTION);

			verify(noticeService, never()).getNoticeList(any());
			assertThat(response.message()).isEqualTo(NO_CONDITION_MESSAGE);
		}

		@Test
		@DisplayName("V7 category만 있어도 조건으로 보고 검색한다")
		void onlyCategory() {
			aiReturns(null, null, "HR", null);

			NoticeAiSearchResponse response = service.search(QUESTION);

			assertThat(searchedRequest().category()).isEqualTo("HR");
			assertThat(response.result()).isNotNull();
		}
	}

	@Nested
	@DisplayName("검색 결과")
	class Result {

		@Test
		@DisplayName("R1 결과가 0건이면 빈 목록과 함께 안내 문구를 준다")
		void noResult() {
			given(noticeService.getNoticeList(any())).willReturn(pageOf(0));
			aiReturns("없는단어", null, null, null);

			NoticeAiSearchResponse response = service.search(QUESTION);

			assertThat(response.result().totalCount()).isZero();
			assertThat(response.result().items()).isEmpty();
			assertThat(response.message()).isEqualTo(NO_RESULT_MESSAGE);
		}

		@Test
		@DisplayName("R2 결과가 있으면 목록을 그대로 담고 안내 문구는 없다")
		void found() {
			aiReturns("정보보안", null, null, "THIS_YEAR");

			NoticeAiSearchResponse response = service.search(QUESTION);

			assertThat(response.result().totalCount()).isEqualTo(1);
			assertThat(response.result().items()).hasSize(1);
			assertThat(response.message()).isNull();
			assertThat(response.condition().keyword()).isEqualTo("정보보안");
			assertThat(response.condition().searchType()).isEqualTo("ALL");
		}

		@Test
		@DisplayName("R3 검색은 1페이지 20건으로 한다")
		void fixedPaging() {
			aiReturns("정보보안", null, null, null);
			service.search(QUESTION);

			NoticeSearchRequest request = searchedRequest();
			assertThat(request.page()).isEqualTo(1);
			assertThat(request.size()).isEqualTo(20);
		}
	}

	@Nested
	@DisplayName("AI에 보내는 내용과 실패 처리")
	class AiCall {

		@Test
		@DisplayName("P1 AI에는 질문만 user 메시지로 보낸다 (공지 데이터를 보내지 않는다)")
		void sendsOnlyQuestion() {
			aiReturns("정보보안", null, null, null);

			service.search(QUESTION);

			// BeanOutputConverter가 JSON 형식 지시문을 user 메시지 뒤에 붙일 수 있으므로, 질문으로 시작하는지와
			// 공지 목록 조회가 AI 호출보다 먼저 일어나지 않았는지(=공지 제목이 들어가지 않았는지)를 본다.
			assertThat(chatModel.lastPrompt).isNotNull();
			String userText = chatModel.lastPrompt.getUserMessage().getText();
			assertThat(userText).startsWith(QUESTION);
			assertThat(chatModel.lastPrompt.getContents()).doesNotContain("정보보안교육 장소 안내");
		}

		@Test
		@DisplayName("F1 모델 호출이 예외로 실패하면 고정 문구의 ExternalServiceException으로 바꾼다")
		void modelFailure() {
			RuntimeException external = new RuntimeException("401 invalid x-api-key: authentication_error");
			chatModel.error = external;

			assertThatThrownBy(() -> service.search(QUESTION))
					.isInstanceOf(ExternalServiceException.class)
					.hasMessage(FAILURE_MESSAGE)
					.hasCause(external);
			verify(noticeService, never()).getNoticeList(any());
		}

		@Test
		@DisplayName("F3 (2026-11-23 장애 재현) thinking이 출력 상한을 다 써서 글 없이 max_tokens로 끝나면 502로 바꾸고, 원인에 max_tokens를 남긴다")
		void emptyAnswerByMaxTokens() {
			// 실제 응답: 빈 thinking 블록 + text 없음 + stop_reason=max_tokens → Converter가 빈 문자열을 받아 Jackson 오류가 났다.
			chatModel.text = "";
			chatModel.withThinking = true;
			chatModel.finishReason = "max_tokens";

			assertThatThrownBy(() -> service.search(QUESTION))
					.isInstanceOf(ExternalServiceException.class)
					.hasMessage(FAILURE_MESSAGE)
					// 응답 메시지는 고정 문구 그대로 두고, 공통 처리기가 log.error로 남기는 cause에서 원인을 알 수 있어야 한다.
					.cause()
					.hasMessageContaining("max_tokens");
			verify(noticeService, never()).getNoticeList(any());
		}

		@Test
		@DisplayName("F4 이 호출은 출력 상한 1024토큰, effort LOW로 요청한다 (thinking이 상한을 다 쓰지 않도록)")
		void requestOptions() {
			aiReturns("정보보안", null, null, null);

			service.search(QUESTION);

			assertThat(chatModel.lastPrompt.getOptions()).isInstanceOf(AnthropicChatOptions.class);
			AnthropicChatOptions options = (AnthropicChatOptions) chatModel.lastPrompt.getOptions();
			assertThat(options.getMaxTokens()).isEqualTo(1024);
			assertThat(options.getOutputConfig()).isNotNull();
			assertThat(options.getOutputConfig().effort()).contains(OutputConfig.Effort.LOW);
		}

		@Test
		@DisplayName("F2 AI가 JSON이 아닌 답을 주면 ExternalServiceException으로 바꾼다")
		void notJson() {
			chatModel.text = "죄송하지만 조건을 찾을 수 없어요.";

			assertThatThrownBy(() -> service.search(QUESTION))
					.isInstanceOf(ExternalServiceException.class)
					.hasMessage(FAILURE_MESSAGE);
			verify(noticeService, never()).getNoticeList(any());
		}
	}
}
