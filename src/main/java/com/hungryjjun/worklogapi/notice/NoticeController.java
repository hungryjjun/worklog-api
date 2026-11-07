package com.hungryjjun.worklogapi.notice;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notices")
public class NoticeController {

	private final NoticeService noticeService;
	
	public NoticeController(NoticeService noticeService) {
		this.noticeService = noticeService;
	}
	
	@GetMapping
	public List<Notice> getNotices(){
		return noticeService.getNoticeList();
	}

	@GetMapping("/{noticeSeq}")
	public ResponseEntity<Notice> getNotice(@PathVariable Long noticeSeq) {
		return noticeService.getNotice(noticeSeq)
				.map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}
	
	@PostMapping
	public ResponseEntity<Map<String, Long>> createNotice(@RequestBody NoticeCreateRequest request) {
		Long noticeSeq = noticeService.createNotice(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("noticeSeq", noticeSeq));
	}
}
