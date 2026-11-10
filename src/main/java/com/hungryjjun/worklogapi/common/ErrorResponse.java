package com.hungryjjun.worklogapi.common;

import java.util.List;

public record ErrorResponse(
		int status,
		String message,
		List<FieldErrorDetail> errors
) {

	public record FieldErrorDetail(
			String field,
			String message
	) {}
}
