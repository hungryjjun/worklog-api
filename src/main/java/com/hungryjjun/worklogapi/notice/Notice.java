package com.hungryjjun.worklogapi.notice;

import java.time.LocalDateTime;

public class Notice {
	
	private Long noticeSeq;
	private String category;
	private String title;
	private String content;
	private Integer viewCnt;
	private String createEmpNo;
	private LocalDateTime createAt;
	
	public Long getNoticeSeq() {
		return noticeSeq;
	}
	public void setNoticeSeq(Long noticeSeq) {
		this.noticeSeq = noticeSeq;
	}
	public String getCategory() {
		return category;
	}
	public void setCategory(String category) {
		this.category = category;
	}
	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public String getContent() {
		return content;
	}
	public void setContent(String content) {
		this.content = content;
	}
	public Integer getViewCnt() {
		return viewCnt;
	}
	public void setViewCnt(Integer viewCnt) {
		this.viewCnt = viewCnt;
	}
	public String getCreateEmpNo() {
		return createEmpNo;
	}
	public void setCreateEmpNo(String createEmpNo) {
		this.createEmpNo = createEmpNo;
	}
	public LocalDateTime getCreateAt() {
		return createAt;
	}
	public void setCreateAt(LocalDateTime createAt) {
		this.createAt = createAt;
	}

}
