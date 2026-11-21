package com.hungryjjun.worklogapi.common;

// 외부 서비스(Claude API 등) 호출이 실패했을 때 던진다. GlobalExceptionHandler에서 502로 변환한다.
// message에는 사용자에게 보여줄 문구를 넣고, 외부 오류는 cause로 보관해 로그에만 남긴다.
public class ExternalServiceException extends RuntimeException {

	public ExternalServiceException(String message, Throwable cause) {
		super(message, cause);
	}
}
