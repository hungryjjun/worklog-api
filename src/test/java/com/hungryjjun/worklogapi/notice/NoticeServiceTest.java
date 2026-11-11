package com.hungryjjun.worklogapi.notice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hungryjjun.worklogapi.common.ConflictException;

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
