package com.hungryjjun.worklogapi.notice;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface NoticeMapper {

	// 목록과 건수는 같은 검색 조건(cond)을 쓴다. XML에서 <include>로 WHERE 절을 공유한다.
	List<NoticeSummary> selectNoticeList(@Param("cond") NoticeSearchCondition cond, @Param("size") int size, @Param("offset") long offset);
	long countNoticeList(@Param("cond") NoticeSearchCondition cond);
	int insertNotice(Notice notice);
	Notice selectNotice(Long noticeSeq);
	int updateViewCnt(Long noticeSeq);
	boolean existsRecentDuplicate(Notice notice);
}
