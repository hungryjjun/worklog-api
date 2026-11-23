# 설계 결정

## 첨부파일

### 구조

- 첨부파일은 공통 그룹 구조로 관리한다 (`attach_file` 1 : N `attach_file_detail`).
- 업무 테이블은 `atch_file_id`만 보유하고, 업무 구분은 `attach_file.se_cd`로 관리한다.
- `notice.atch_file_id`에는 FK를 걸지 않는다. 공통 구조이므로 애플리케이션에서 관리한다.

### 삭제와 정리

- 공지 삭제는 `del_yn` 소프트 삭제로 처리하고, 이때 연결된 `attach_file.use_yn`을 `'N'`으로 변경한다.
- 고아 파일은 `use_yn = 'N'` 기준으로 배치 정리할 예정이다.

## CORS

- `WebConfig`에서 `/api/**`에 전체 설정으로 적용한다.
- 허용 주소는 현재 로컬 2개뿐이므로 코드에 직접 작성한다.
- 배포 계획이 생기면 허용 주소를 `application.properties`로 분리한다.
- 컨트롤러별 `@CrossOrigin`은 사용하지 않는다. 모든 API의 정책이 같고, 새 컨트롤러에서 누락되는 일을 막기 위해서다.
- 허용 주소에 `*`는 쓰지 않는다. 임의의 사이트가 API 응답을 읽을 수 있게 되고, 쿠키 인증에 필요한 `allowCredentials(true)`와 함께 쓸 수 없기 때문이다.
- `config` 패키지는 특정 기능에 속하지 않는 공통 설정을 두는 곳으로, 기능별 패키지 규칙의 예외로 둔다.

## 입력 검증

### 방식

- 요청 DTO(record) 필드에 Bean Validation 어노테이션(`@NotBlank`, `@Size`, `@Pattern`)을 붙이고, 컨트롤러에서 `@Valid`로 검증한다.
- 화면 검증과 별개로 백엔드에서도 반드시 검증한다. API는 화면을 거치지 않고 직접 호출될 수 있기 때문이다.
- 오류 메시지는 어노테이션의 `message`에 한국어로 직접 작성한다. 메시지가 적은 동안은 규칙 옆에 두는 편이 읽기 쉽다.
- 코드성 값(공지 구분 등)은 `String` + `@Pattern`으로 검증한다. enum으로 받으면 잘못된 값이 검증 전 JSON 변환 단계에서 실패해 오류 처리 경로가 둘로 나뉘기 때문이다.

### 실패 응답

- 검증 실패는 `GlobalExceptionHandler`(`@RestControllerAdvice`)에서 한곳에서 처리하고, 컨트롤러마다 처리하지 않는다.
- 응답은 400 Bad Request와 직접 정의한 `ErrorResponse` 형식(`status`, `message`, `errors[{field, message}]`)으로 한다.
- 한 필드에 규칙 위반이 여러 개면 모두 반환한다. 필드마다 하나만 보여주려면 검사 순서를 따로 지정해야 하는데, 그만큼 복잡해질 필요가 없다고 판단했다.
- 본문 누락, JSON 문법 오류, 타입 불일치(`HttpMessageNotReadableException`)도 같은 형식의 400으로 응답한다. 특정 필드 오류가 아니므로 `errors`는 빈 배열로 둔다.
- 이 경우 응답 메시지는 고정 문구만 쓰고, 예외 메시지는 서버 로그에만 남긴다. 예외 메시지에 파서 클래스명, 위치 정보, 자바 타입명 같은 내부 정보가 들어 있기 때문이다.

### 테스트

- 검증과 오류 응답은 `@WebMvcTest`로 웹 계층만 띄워 테스트하고, Service는 `@MockitoBean`으로 대체한다. DB 없이 실행하기 위해서다.

### common 패키지

- 여러 기능이 함께 쓰는 코드(공통 예외 처리, 공통 응답 형식)는 `common` 패키지에 둔다.
- `config`는 Spring 설정(`WebConfig` 등)만 두고, `common`과 구분한다.

## 중복 등록 방지

### 규칙

- 같은 작성자 사번, 같은 제목, 같은 내용의 공지가 10초 안에 다시 등록되면 409 Conflict로 거부한다. 삭제된 공지(`del_yn = 'Y'`)는 비교하지 않는다.
- 시간은 DB 시계(`NOW()`)로 판단한다. `create_at`도 DB가 채우므로 같은 시계로 비교해야 앱 서버와 DB 서버의 시계·시간대 차이에 영향을 받지 않는다.
- 제목과 내용은 `COLLATE utf8mb4_bin`으로 정확히 일치할 때만 중복으로 본다. 테이블 collation(`utf8mb4_0900_ai_ci`)은 대소문자·악센트를 무시하는데, 더블클릭·재전송은 값이 완전히 같으므로 정확히 비교해도 놓치지 않고 잘못 거부하는 경우만 줄어든다.
- `create_at`이 초 단위라서 실제 판단 구간은 10~11초 사이로 약간 흔들린다. 중복 방지 용도로는 허용한다.

### 409 응답

- 충돌 상황은 `common/ConflictException`으로 던지고 `GlobalExceptionHandler`에서 409로 변환한다. 기능별 예외를 만들지 않은 것은 `common`이 기능 패키지에 의존하지 않게 하고, 다른 기능의 409에도 재사용하기 위해서다.
- `ConflictException`의 메시지는 Service에서 사용자용 문구로 직접 작성하므로 응답에 그대로 쓴다. 내부 정보가 담긴 예외 메시지를 넣지 않도록 주의한다.

### 한계

- 조회 후 삽입 방식이라 **정확히 동시에 도착한 두 요청은 둘 다 통과할 수 있다.** 둘 다 상대의 INSERT 전에 SELECT하기 때문이며, `@Transactional`만으로는 막히지 않는다(REPEATABLE READ의 일반 SELECT는 잠금 없이 스냅샷을 읽는다).
- 동시 요청까지 막아야 하면 멱등성 키(클라이언트가 등록 시도마다 UUID를 보내고 서버가 UNIQUE 제약으로 저장)를 도입한다. 화면에서는 등록 중 버튼 비활성화를 함께 적용한다.
- 중복 조회용 인덱스 `(create_emp_no, create_at)`는 더미 데이터로 실행계획을 비교하는 작업에서 검토한다.

## 목록 페이징

### 요청

- `GET /api/<plural>?page=1&size=20` 형태로 받는다. `page`는 1부터 시작하고 기본값 1, `size`는 기본값 20이며 20, 50, 100만 허용한다.
- 파라미터는 요청 record(`NoticeSearchRequest`)로 받고, 기본값은 compact 생성자에서 채운다. 허용값 검증은 재사용할 수 있도록 커스텀 어노테이션 `@AllowedValues`로 만든다.
- 잘못된 값은 기존 공통 처리기로 400을 응답한다. 숫자가 아닌 값(`page=abc`)의 기본 메시지에는 자바 타입명이 들어 있으므로 "값의 형식이 올바르지 않습니다."라는 고정 문구로 바꾼다.
- `offset`은 `long`으로 계산한다. 큰 `page` 값에서 `int` 곱셈이 넘쳐 음수 OFFSET(SQL 오류, 500)이 되는 것을 막기 위해서다.

### 응답

- 공통 `PageResponse<T>(items, totalCount, page, size, totalPages)`로 응답한다. 페이지 번호 묶음(5개씩 등) 계산은 화면이 담당하고 API에는 넣지 않는다. 전자정부 `PaginationInfo`의 `pageSize`(묶음 크기) 역할이 화면으로 넘어간 것이다.
- `totalPages`는 올림 나눗셈(`Math.ceilDiv`)으로 계산하며, 0건이면 0이다. 전자정부 식(`(total - 1) / n + 1`)은 0건에 1을 내지만, 실제 페이지가 없으므로 0이 정확하다고 판단했다. 화면은 0건이면 안내 문구를 보여주고 페이징을 숨긴다.
- 전체 페이지보다 큰 `page`는 200과 빈 `items`로 응답하고 메타정보는 정상값으로 채운다. 데이터 삭제로 페이지 수가 줄어드는 경우도 자연스럽게 처리되고, 요청값과 응답값이 달라지는 보정 방식보다 예측하기 쉽기 때문이다. 화면은 `items`가 비었는데 `totalPages > 0`이면 마지막 페이지로 이동한다.

### 조회

- 목록 항목은 상세용 `Notice`와 분리한 읽기 전용 record(`NoticeSummary`)로 내려준다. 목록 화면에 필요 없는 `content`(TEXT)를 조회하지 않고, `@JsonInclude(NON_NULL)`처럼 다른 null 필드까지 숨기는 부작용 없이 목록 응답 필드를 코드로 고정하기 위해서다.
- record는 setter가 없으므로 MyBatis `<constructor>` 매핑에 `name`과 `javaType`을 명시한다.
- 건수(`COUNT(*)`)와 목록(`LIMIT ? OFFSET ?`)은 쿼리를 나눈다. `SQL_CALC_FOUND_ROWS`는 deprecated이고, `COUNT(*) OVER()`는 0건일 때 전체 건수를 얻을 수 없다.
- 두 쿼리는 `@Transactional(readOnly = true)` 안에서 실행해 같은 스냅샷을 읽게 한다. 그 사이의 등록·삭제로 건수와 목록이 어긋나지 않게 하기 위해서다.
- `offset >= totalCount`(0건 포함)이면 결과가 빈 목록이 확실하므로 목록 쿼리를 생략한다.

### 한계

- OFFSET은 앞의 행을 모두 읽고 버리므로 뒤 페이지일수록 느려진다. 데이터가 많아지면 마지막으로 본 `notice_seq` 기준으로 다음 목록을 가져오는 방식(no-offset)을 검토한다.
- 인덱스는 더미 데이터로 실행계획을 비교하면서 추가한다.

## 목록 검색 조건

### 요청

- 기존 `GET /api/notices`에 `from`, `to`, `category`, `searchType`, `keyword`를 추가한다. 모두 선택 사항이며 페이징과 함께 쓴다. 이후 AI 챗봇이 문장에서 뽑은 조건으로 같은 API를 호출한다.
- 빈 값 정리와 기본값은 `NoticeSearchRequest`의 compact 생성자에서 처리한다. 검증은 생성자 다음에 실행되므로, `category=`처럼 빈 값은 `@Pattern`에 걸리지 않고 "조건 없음"이 된다.
- `from > to`는 클래스 단위 검증 `@ValidPeriod`(`common/validation`, `DateRange` 인터페이스 기반)로 막고, 오류는 `to` 필드로 보고한다. 클래스 단위 위반은 기본적으로 필드에 속하지 않아 공통 처리기의 `errors`에 담기지 않기 때문이다.

### 기간: to 날짜 포함 방식

- `to=2026-11-10`은 11월 10일 등록 글까지 포함한다. SQL은 `create_at >= from AND create_at < to + 1일`의 반열린 구간으로 비교하고, `+1일`은 Java(`LocalDate.plusDays(1)`)에서 계산한다.
- `DATE(create_at) <= to`는 컬럼에 함수를 씌우므로 이후 `create_at` 인덱스를 쓸 수 없다.
- `create_at <= 'to 23:59:59'`는 컬럼이 소수초(`datetime(3)` 등)로 바뀌면 23:59:59.5 같은 값을 놓친다. 다음 날 0시 미만이면 정밀도와 무관하게 정확하다.

### 검색어: LIKE 특수문자 처리

- 부분 일치(`LIKE CONCAT('%', ?, '%')`)이며, 검색어의 `%`, `_`는 일반 글자로 검색한다.
- 이스케이프 문자로 `!`를 쓰고 SQL에 `ESCAPE '!'`를 명시한다. Java에서 `!`→`!!`, `%`→`!%`, `_`→`!_` 순서로 치환하며, `!`를 먼저 바꿔야 뒤에서 붙인 `!`가 다시 치환되지 않는다.
- MySQL 기본 이스케이프 문자인 `\`를 쓰지 않는 이유는 MySQL 문자열 리터럴, XML, Java 문자열에서 각각 다시 이스케이프해야 해서 실수하기 쉽기 때문이다. `ESCAPE '!'`를 지정하면 `\`도 일반 글자로 검색된다.
- 검색어는 앞뒤 공백을 제거하고, 남는 글자가 없으면 조건에서 뺀다. 20자 제한은 공백을 제거한 길이 기준이다.
- 테이블 collation(`utf8mb4_0900_ai_ci`)이 대소문자를 구분하지 않으므로 영문 검색어도 대소문자 구분 없이 찾는다. 중복 방지와 달리 검색은 넓게 찾는 편이 맞으므로 그대로 둔다.

### 검색 유형 기본값

- `searchType`이 없으면 `ALL`(제목 + 내용)이다. 사용자나 AI가 유형을 지정하지 않았을 때는 놓치는 글이 없도록 범위를 넓히는 편이 낫다고 판단했다. 제목만 원하면 명시적으로 `TITLE`을 보낸다.

### 화면 검증과 서버 검증의 역할

- 화면 검증은 **입력 편의**를 위한 것이다. date picker, 구분 select, 검색어 `maxlength`, 시작일·종료일 선후 안내로 잘못된 요청을 미리 줄인다.
- 서버 검증은 **최종 판단**이다. API는 화면을 거치지 않고 직접 호출될 수 있고, 이후에는 AI가 문장에서 뽑은 조건으로 호출하므로 값이 형식에 맞는다는 보장이 없다. 모든 조건은 화면 검증 여부와 관계없이 서버에서 다시 검증한다.
- 두 검증의 규칙(허용값, 길이, 날짜 형식)은 같게 유지하고, 서버 규칙이 기준이다.

### 한계

- `%검색어%`는 앞이 와일드카드라 B-Tree 인덱스를 쓸 수 없어 전체 행을 읽는다. 데이터가 많아지면 FULLTEXT 인덱스(ngram)를 검토하고, 더미 데이터로 실행계획을 비교하는 작업에서 기간·구분 인덱스와 함께 본다.

## AI 채팅

### 연동 방식

- Spring AI 2.0.2(`spring-ai-bom`)와 `spring-ai-starter-model-anthropic`으로 Claude API를 호출한다. 2.0.x는 Spring Boot 4.0.x·4.1.x를 지원하고, 2.1.x는 Boot 4.2 대상이라 쓰지 않는다.
- 설정 속성은 2.0.2 jar의 메타데이터 기준으로 `spring.ai.anthropic.chat.model`, `chat.max-tokens`를 쓴다. 1.x 시절의 `chat.options.*`는 2.0에서 deprecated다.
- Service는 `ChatClient`(`prompt().user().call().content()`)로 호출한다. 테스트에서는 `ChatModel.call(Prompt)`만 가짜로 구현해 넣으므로 실제 API를 호출하지 않는다.

### 모델

- `claude-haiku-5-5`를 쓴다. Claude 모델 중 가장 저렴하고 빠르며, 이후 단계의 목표(문장에서 검색 조건을 뽑는 일)에는 상위 모델이 필요하지 않다고 판단했다. 품질이 부족하면 그때 비교한다.

### API 키

- 키는 환경변수 `WORKLOG_ANTHROPIC_API_KEY`로만 주입한다(`spring.ai.anthropic.api-key=${WORKLOG_ANTHROPIC_API_KEY}`).
- 일반적인 이름인 `ANTHROPIC_API_KEY`를 쓰지 않는 이유는 VS Code의 Claude Code가 이 변수를 발견하면 구독 인증 대신 API 키 과금으로 동작하기 때문이다. 프로젝트 전용 접두어로 충돌을 피한다.
- 키가 없으면 앱 시작이 실패한다(`DB_PASSWORD`와 같은 방식). 테스트는 `@SpringBootTest`에만 더미 키를 넣어 키 없는 PC에서도 통과한다.

### 비용 상한

- Console은 선불 크레딧이고 자동 충전을 꺼 두었으므로, 한 번의 호출 비용을 코드와 설정으로 제한한다.
- 입력: 질문 500자 이하(`@Size(max = 500)`). system 지시문은 "한국어로 간결하게 답한다." 한 줄만 둔다.
- 출력: 최대 500토큰(`spring.ai.anthropic.chat.max-tokens=500`). 간결하게 답하라는 지시로 답이 중간에 잘리는 일을 줄인다.
- 장애 시: 타임아웃 30초, 재시도 1회(`timeout=30s`, `max-retries=1`). 기본값(60초, 2회)이면 최악의 경우 3분 가까이 기다린 뒤에야 실패 응답을 받는다.

### 실패 응답

- AI 호출 실패(키 오류, 시간 초과, 크레딧 소진, 빈 응답)는 `common/ExternalServiceException`으로 감싸고 `GlobalExceptionHandler`에서 **502 Bad Gateway**로 응답한다. 메시지는 "AI 응답을 받지 못했습니다. 잠시 후 다시 시도해 주세요."로 고정한다.
- 502를 고른 이유: 우리 서버는 정상인데 상위(upstream) 서비스가 실패한 상황을 뜻하는 코드이기 때문이다. 500은 우리 서버의 버그와 구분되지 않고, 503은 우리 서버 자체가 일시적으로 사용 불가라는 뜻이며, 504는 시간 초과에만 해당한다. 화면 입장에서 원인별 처리가 다르지 않으므로 502 하나로 통일한다.
- Spring AI·Anthropic SDK의 예외 종류가 문서에 정리되어 있지 않아, Service의 외부 호출 경계에서 `RuntimeException`을 모두 감싼다.
- 외부 오류 메시지는 응답에 넣지 않고 cause까지 로그로만 남긴다. 인증 실패나 계정 상태 같은 내부 정보가 들어 있기 때문이다.
- 예외 이름을 AI 전용이 아닌 `ExternalServiceException`으로 둔 것은 `common`이 기능 패키지에 의존하지 않게 하고, 이후 다른 외부 연동에도 재사용하기 위해서다.
