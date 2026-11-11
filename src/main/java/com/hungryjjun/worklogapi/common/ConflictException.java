package com.hungryjjun.worklogapi.common;

// 요청이 현재 데이터 상태와 충돌할 때(중복 등록 등) 던진다. GlobalExceptionHandler에서 409로 변환한다.
public class ConflictException extends RuntimeException {

	public ConflictException(String message) {
		super(message);
	}
}
