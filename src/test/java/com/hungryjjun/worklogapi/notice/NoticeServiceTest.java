package com.hungryjjun.worklogapi.notice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hungryjjun.worklogapi.common.ConflictException;
import com.hungryjjun.worklogapi.common.PageResponse;

// Spring 없이 Service만 테스트한다. Mapper는 Mockito mock으로 대체한다.
@ExtendWith(MockitoExtension.class)
class NoticeServiceTest {

	@Mock
	private NoticeMapper noticeMapper;

	private NoticeService noticeService;

	private final NoticeCreateRequest request = new NoticeCreateRequest("GENERAL", "제목", "내용", "E001");

	@BeforeEach
	void setUp() {
		noticeService = new NoticeService(noticeMapper);
	}

	@Nested
	@DisplayName("목록 조회 페이징")
	class GetNoticeList {

		private final NoticeSummary summary =
				new NoticeSummary(1L, "GENERAL", "제목", 0, "E001", LocalDateTime.of(2026, 11, 13, 9, 0));

		@Test
		@DisplayName("L1 page와 size로 offset을 계산해 Mapper에 전달한다 (page=3, size=20 → offset 40)")
		void passOffset() {
			given(noticeMapper.countNoticeList()).willReturn(235L);
			given(noticeMapper.selectNoticeList(20, 40L)).willReturn(List.of(summary));

			PageResponse<NoticeSummary> response = noticeService.getNoticeList(new NoticeSearchRequest(3, 20));

			verify(noticeMapper).selectNoticeList(20, 40L);
			assertThat(response.items()).containsExactly(summary);
			assertThat(response.totalCount()).isEqualTo(235);
			assertThat(response.page()).isEqualTo(3);
			assertThat(response.size()).isEqualTo(20);
			assertThat(response.totalPages()).isEqualTo(12);
		}

		@Test
		@DisplayName("L2 0건이면 목록 쿼리를 실행하지 않고 빈 목록, 0페이지를 반환한다")
		void zeroCount() {
			given(noticeMapper.countNoticeList()).willReturn(0L);

			PageResponse<NoticeSummary> response = noticeService.getNoticeList(new NoticeSearchRequest(null, null));

			verify(noticeMapper, never()).selectNoticeList(anyInt(), anyLong());
			assertThat(response.items()).isEmpty();
			assertThat(response.totalPages()).isZero();
			assertThat(response.page()).isEqualTo(1);
			assertThat(response.size()).isEqualTo(20);
		}

		@Test
		@DisplayName("L3 전체 페이지보다 큰 page면 목록 쿼리 없이 빈 목록과 정상 메타정보를 반환한다")
		void pageBeyondTotal() {
			given(noticeMapper.countNoticeList()).willReturn(235L);

			PageResponse<NoticeSummary> response = noticeService.getNoticeList(new NoticeSearchRequest(99, 20));

			verify(noticeMapper, never()).selectNoticeList(anyInt(), anyLong());
			assertThat(response.items()).isEmpty();
			assertThat(response.totalCount()).isEqualTo(235);
			assertThat(response.page()).isEqualTo(99);
			assertThat(response.totalPages()).isEqualTo(12);
		}

		@Test
		@DisplayName("L4 마지막 페이지는 목록 쿼리를 실행한다 (235건, page=12 → offset 220)")
		void lastPage() {
			given(noticeMapper.countNoticeList()).willReturn(235L);
			given(noticeMapper.selectNoticeList(20, 220L)).willReturn(List.of(summary));

			PageResponse<NoticeSummary> response = noticeService.getNoticeList(new NoticeSearchRequest(12, 20));

			assertThat(response.items()).hasSize(1);
		}
	}

	@Nested
	@DisplayName("공지 등록 중복 방지")
	class CreateNoticeDuplicate {

		@Test
		@DisplayName("S1 중복이 없으면 등록하고 발급된 noticeSeq를 반환한다")
		void createWhenNoDuplicate() {
			given(noticeMapper.existsRecentDuplicate(any(Notice.class))).willReturn(false);
			// MyBatis의 useGeneratedKeys + keyProperty가 PK를 채워 주는 동작을 흉내 낸다.
			given(noticeMapper.insertNotice(any(Notice.class))).willAnswer(invocation -> {
				Notice notice = invocation.getArgument(0);
				notice.setNoticeSeq(10L);
				return 1;
			});

			Long noticeSeq = noticeService.createNotice(request);

			assertThat(noticeSeq).isEqualTo(10L);
			verify(noticeMapper).insertNotice(any(Notice.class));
		}

		@Test
		@DisplayName("S2 중복이 있으면 ConflictException을 던지고 등록하지 않는다")
		void rejectWhenDuplicate() {
			given(noticeMapper.existsRecentDuplicate(any(Notice.class))).willReturn(true);

			assertThatThrownBy(() -> noticeService.createNotice(request))
					.isInstanceOf(ConflictException.class)
					.hasMessage("같은 내용의 공지가 방금 등록되었습니다. 잠시 후 다시 시도해 주세요.");

			verify(noticeMapper, never()).insertNotice(any(Notice.class));
		}

		@Test
		@DisplayName("S3 중복 조회에 작성자 사번, 제목, 내용을 그대로 전달한다")
		void passRequestValuesToDuplicateCheck() {
			given(noticeMapper.existsRecentDuplicate(any(Notice.class))).willReturn(false);

			noticeService.createNotice(request);

			ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
			verify(noticeMapper).existsRecentDuplicate(captor.capture());
			Notice checked = captor.getValue();
			assertThat(checked.getCreateEmpNo()).isEqualTo("E001");
			assertThat(checked.getTitle()).isEqualTo("제목");
			assertThat(checked.getContent()).isEqualTo("내용");
		}
	}
}
