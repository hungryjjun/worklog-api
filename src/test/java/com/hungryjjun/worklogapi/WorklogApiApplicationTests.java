package com.hungryjjun.worklogapi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// 컨텍스트 로딩만 확인하므로 Claude API는 호출하지 않는다.
// 키 환경변수가 없는 PC에서도 통과하도록 더미 키를 넣는다.
@SpringBootTest(properties = "spring.ai.anthropic.api-key=test-dummy-key")
class WorklogApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
