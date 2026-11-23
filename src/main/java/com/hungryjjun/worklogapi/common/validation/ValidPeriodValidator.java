package com.hungryjjun.worklogapi.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

// from이 to보다 늦으면 위반이다. 둘 중 하나라도 없으면 기간 한쪽이 열린 검색이므로 통과한다.
public class ValidPeriodValidator implements ConstraintValidator<ValidPeriod, DateRange> {

	@Override
	public boolean isValid(DateRange value, ConstraintValidatorContext context) {
		if (value == null || value.from() == null || value.to() == null) {
			return true;
		}
		if (!value.from().isAfter(value.to())) {
			return true;
		}

		// 클래스 단위 위반은 기본적으로 특정 필드에 속하지 않아 공통 처리기의 errors에 담기지 않는다.
		// 기본 위반을 끄고 to 필드의 위반으로 다시 등록하면 Spring이 FieldError("to")로 변환한다.
		context.disableDefaultConstraintViolation();
		context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
				.addPropertyNode("to")
				.addConstraintViolation();
		return false;
	}
}
