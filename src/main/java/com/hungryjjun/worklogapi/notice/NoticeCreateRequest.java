package com.hungryjjun.worklogapi.notice;

public record NoticeCreateRequest(
		String category,
		String title,
		String content,
		String createEmpNo
) {}
