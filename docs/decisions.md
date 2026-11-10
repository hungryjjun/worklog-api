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
