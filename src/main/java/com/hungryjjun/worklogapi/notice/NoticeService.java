package com.hungryjjun.worklogapi.notice;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class NoticeService {
	
	private final NoticeMapper noticeMapper;
	
	public NoticeService(NoticeMapper noticeMapper) {
		this.noticeMapper = noticeMapper;
	}
	
	public List<Notice> getNoticeList(){
		return noticeMapper.selectNoticeList();
	}
	
	public Long createNotice(NoticeCreateRequest request) {
		Notice notice = new Notice();
		notice.setCategory(request.category());
		notice.setTitle(request.title());
		notice.setContent(request.content());
		notice.setCreateEmpNo(request.createEmpNo());
		
		noticeMapper.insertNotice(notice);
		return notice.getNoticeSeq();
	}

}
