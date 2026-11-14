package com.hungryjjun.worklogapi.common;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	// @Valid 검증 실패 시 400과 항목별 오류 메시지를 반환한다.
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
		List<ErrorResponse.FieldErrorDetail> errors = e.getBindingResult().getFieldErrors().stream()
				.map(fieldError -> new ErrorResponse.FieldErrorDetail(fieldError.getField(), toMessage(fieldError)))
				.toList();

		ErrorResponse body = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "입력값이 올바르지 않습니다.", errors);
		return ResponseEntity.badRequest().body(body);
	}

	// 타입 변환 실패(page=abc 등)의 기본 메시지에는 자바 타입명 등 내부 정보가 들어 있으므로 고정 문구로 바꾼다.
	private String toMessage(FieldError fieldError) {
		if (fieldError.isBindingFailure()) {
			return "값의 형식이 올바르지 않습니다.";
		}
		return fieldError.getDefaultMessage();
	}

	// 요청 본문이 없거나 JSON 문법·타입이 맞지 않을 때 400을 반환한다.
	// 예외 메시지에는 파서 클래스명, 줄·열 위치, 자바 타입명이 들어 있으므로 응답에 넣지 않고 로그로만 남긴다.
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
		log.warn("요청 본문 해석 실패: {}", e.getMessage());

		ErrorResponse body = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "요청 본문의 형식이 올바르지 않습니다.", List.of());
		return ResponseEntity.badRequest().body(body);
	}

	// 중복 등록 등 데이터 상태와 충돌하면 409를 반환한다.
	// ConflictException의 메시지는 Service에서 사용자용 문구로 직접 작성하므로 그대로 응답한다.
	@ExceptionHandler(ConflictException.class)
	public ResponseEntity<ErrorResponse> handleConflict(ConflictException e) {
		ErrorResponse body = new ErrorResponse(HttpStatus.CONFLICT.value(), e.getMessage(), List.of());
		return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
	}
}
