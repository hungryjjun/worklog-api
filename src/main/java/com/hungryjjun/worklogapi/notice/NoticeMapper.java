package com.hungryjjun.worklogapi.notice;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface NoticeMapper {

	List<NoticeSummary> selectNoticeList(@Param("size") int size, @Param("offset") long offset);
	long countNoticeList();
	int insertNotice(Notice notice);
	Notice selectNotice(Long noticeSeq);
	int updateViewCnt(Long noticeSeq);
	boolean existsRecentDuplicate(Notice notice);
}
