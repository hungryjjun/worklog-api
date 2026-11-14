package com.hungryjjun.worklogapi.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

// 정수 값이 values에 나열된 값 중 하나인지 검증한다. null은 통과한다(필수 여부는 @NotNull로 따로 검사).
@Documented
@Constraint(validatedBy = AllowedValuesValidator.class)
@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface AllowedValues {

	int[] values();

	String message() default "허용되지 않은 값입니다.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
