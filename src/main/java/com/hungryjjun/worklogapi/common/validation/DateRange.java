package com.hungryjjun.worklogapi.common.validation;

import java.time.LocalDate;

// 기간(from ~ to)을 가진 요청 객체. @ValidPeriod가 이 인터페이스로 두 날짜를 읽는다.
// record의 구성요소 이름이 from, to이면 자동으로 생기는 접근자 from(), to()가 이 메서드들의 구현이 된다.
public interface DateRange {

	LocalDate from();

	LocalDate to();
}
