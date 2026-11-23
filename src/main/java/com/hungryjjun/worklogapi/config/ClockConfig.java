package com.hungryjjun.worklogapi.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// "오늘"을 정하는 시계. 코드에서 LocalDate.now()를 직접 부르지 않고 이 Bean을 주입받아,
// 서버 기본 시간대와 상관없이 한국 시간으로 계산하고 테스트에서는 고정 시계로 바꿔 끼운다.
@Configuration
public class ClockConfig {

	@Bean
	public Clock clock() {
		return Clock.system(ZoneId.of("Asia/Seoul"));
	}
}
