package com.hungryjjun.worklogapi.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

// 클래스 단위 검증: DateRange의 from이 to보다 늦으면 위반이다. 날짜가 하나라도 없으면 통과한다.
// 위반은 to 필드의 오류로 보고한다.
@Documented
@Constraint(validatedBy = ValidPeriodValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPeriod {

	String message() default "시작일은 종료일보다 늦을 수 없습니다.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
