package com.hungryjjun.worklogapi.notice;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

// 기존 NoticeController와 그 테스트를 건드리지 않도록 AI 검색은 컨트롤러를 따로 둔다. URL은 같은 /api/notices 아래다.
@RestController
@RequestMapping("/api/notices")
public class NoticeAiSearchController {

	private final NoticeAiSearchService noticeAiSearchService;

	public NoticeAiSearchController(NoticeAiSearchService noticeAiSearchService) {
		this.noticeAiSearchService = noticeAiSearchService;
	}

	@PostMapping("/ai-search")
	public NoticeAiSearchResponse search(@Valid @RequestBody NoticeAiSearchRequest request) {
		return noticeAiSearchService.search(request.question());
	}
}
