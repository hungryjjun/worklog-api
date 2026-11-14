package com.hungryjjun.worklogapi.notice;

import java.time.LocalDateTime;

// 목록 화면용 읽기 전용 결과. 본문(content)은 상세 조회에서만 내려준다.
// MyBatis는 NoticeMapper.xml의 noticeSummaryMap(constructor 매핑)으로 생성한다.
public record NoticeSummary(
		Long noticeSeq,
		String category,
		String title,
		Integer viewCnt,
		String createEmpNo,
		LocalDateTime createAt
) {}
