package com.hungryjjun.worklogapi.notice;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hungryjjun.worklogapi.common.ConflictException;
import com.hungryjjun.worklogapi.common.PageResponse;

@Service
public class NoticeService {

	private static final String DUPLICATE_MESSAGE = "같은 내용의 공지가 방금 등록되었습니다. 잠시 후 다시 시도해 주세요.";

	private final NoticeMapper noticeMapper;
	
	public NoticeService(NoticeMapper noticeMapper) {
		this.noticeMapper = noticeMapper;
	}
	
	// 건수와 목록을 같은 읽기 전용 트랜잭션에서 조회해, 두 쿼리 사이의 등록·삭제로 결과가 어긋나지 않게 한다.
	@Transactional(readOnly = true)
	public PageResponse<NoticeSummary> getNoticeList(NoticeSearchRequest request) {
		long totalCount = noticeMapper.countNoticeList();

		// 0건이거나 요청한 페이지가 범위를 넘으면 결과가 빈 목록이므로 목록 쿼리를 생략한다.
		List<NoticeSummary> items = (request.offset() >= totalCount)
				? List.of()
				: noticeMapper.selectNoticeList(request.size(), request.offset());

		return PageResponse.of(items, totalCount, request.page(), request.size());
	}
	
	public Long createNotice(NoticeCreateRequest request) {
		Notice notice = new Notice();
		notice.setCategory(request.category());
		notice.setTitle(request.title());
		notice.setContent(request.content());
		notice.setCreateEmpNo(request.createEmpNo());

		// 동시에 도착한 요청은 둘 다 통과할 수 있다 (docs/decisions.md 참고).
		if (noticeMapper.existsRecentDuplicate(notice)) {
			throw new ConflictException(DUPLICATE_MESSAGE);
		}

		noticeMapper.insertNotice(notice);
		return notice.getNoticeSeq();
	}

	@Transactional
	public Optional<Notice> getNotice(Long noticeSeq) {
		if (noticeMapper.updateViewCnt(noticeSeq) == 0) {
			return Optional.empty();
		}
		return Optional.ofNullable(noticeMapper.selectNotice(noticeSeq));
	}

}
