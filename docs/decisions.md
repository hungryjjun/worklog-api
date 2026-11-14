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
