package com.hungryjjun.worklogapi.common.validation;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class AllowedValuesValidator implements ConstraintValidator<AllowedValues, Integer> {

	private Set<Integer> allowedValues;

	@Override
	public void initialize(AllowedValues annotation) {
		allowedValues = Arrays.stream(annotation.values())
				.boxed()
				.collect(Collectors.toUnmodifiableSet());
	}

	@Override
	public boolean isValid(Integer value, ConstraintValidatorContext context) {
		return value == null || allowedValues.contains(value);
	}
}
