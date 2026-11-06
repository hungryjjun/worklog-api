package com.hungryjjun.worklogapi.notice;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NoticeMapper {
	
	List<Notice> selectNoticeList();
}
