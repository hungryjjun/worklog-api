# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 정보

- 개인 학습용 사이드프로젝트 (공지사항, AI 챗봇, 근태 예정)
- Java 25, Spring Boot 4.1, Maven, MyBatis(XML 매퍼), MySQL 9.7. JPA는 사용하지 않으며 SQL은 모두 MyBatis XML에 직접 작성한다.
- 개발자는 Java 1.7/Oracle 기반 공공 SI 경력이 있고, 최신 스택을 학습 중이다.

## Commands

Use `./mvnw` (bash) or `.\mvnw.cmd` (PowerShell/cmd).

- Build: `./mvnw clean package`
- Run: `./mvnw spring-boot:run`
- All tests: `./mvnw test`
- Single test class: `./mvnw test -Dtest=WorklogApiApplicationTests`
- Single test method: `./mvnw test -Dtest=WorklogApiApplicationTests#contextLoads`

## Runtime requirements

- A local MySQL at `localhost:3306`, database `worklog`, user `root` (see `src/main/resources/application.properties`).
- The `DB_PASSWORD` environment variable must be set. The app and the `@SpringBootTest` context test both fail without a reachable DB.
- DB 스키마는 `db/schema.sql` 참고

## Architecture

Code is organized **by feature package** under `com.hungryjjun.worklogapi` (e.g. `notice/`). Each feature contains the following:

- `XxxController`: `@RestController` under `/api/<plural>`. Injects the service through the constructor.
- `XxxService`: `@Service`. Maps request records to domain objects and calls the mapper.
- `XxxMapper`: a MyBatis `@Mapper` interface. Each method name must match a statement `id` in `src/main/resources/mapper/<feature>/XxxMapper.xml`, and the XML `namespace` must be the interface's fully qualified name.
- Domain class (e.g. `Notice`): a mutable POJO with getters and setters, used as the MyBatis result and parameter type.
- Request DTOs: Java `record`s (e.g. `NoticeCreateRequest`).

MyBatis conventions (from `application.properties`):
- Mapper XMLs are loaded from `classpath:mapper/**/*.xml`.
- `map-underscore-to-camel-case=true`, so snake_case columns map to camelCase fields without explicit resultMaps.
- Inserts use `useGeneratedKeys="true" keyProperty="..."` to fill the PK on the domain object, which the service then returns.
- Deletes are soft deletes (`del_yn = 'N'` filters in selects).

설계 결정은 `docs/decisions.md` 참고

## 코딩 규칙

- REST API는 `/api/{복수형 리소스}` 형태로 만들고, HTTP 메서드와 상태코드(200, 201 등)를 지킨다.
- 패키지는 기능별(`notice`, `chat` 등)로 나누고, Controller → Service → Mapper → XML 구조를 따른다.
- 의존성은 생성자 주입을 사용하고, 요청 DTO는 `record`로 만든다.
- 테이블·컬럼명은 소문자 snake_case를 쓴다. MyBatis `#{}`에는 Java 필드명을 쓰며, `${}`는 사용하지 않는다.
- 비밀번호·API 키는 환경변수로만 사용하고, 코드와 설정 파일에 직접 쓰지 않는다.
- 소스 파일은 탭으로 들여쓴다.

## 작업 방식

- 모든 답변과 설명은 한국어로 작성한다.
- 작업은 작게, 한 번에 기능 하나씩 진행한다.
- 파일을 바꾸기 전에 무엇을, 왜 바꾸는지 한국어로 먼저 설명한다.
- Java 1.7과 다른 문법이나 방식(record, var, 람다, 스트림 등)은 1.7 방식과 비교해서 설명한다.
- 작업이 끝나면 커밋 메시지 예시를 제안한다. `feat:`, `fix:`, `docs:` 형식에 한국어 설명과 이슈 번호를 붙인다. 예: `feat: 공지사항 등록 API 추가 (closes #2)`
- `git commit`, `git push`는 실행하지 않는다. 사용자가 직접 한다.
- 불확실하거나 선택지가 있으면 먼저 질문한다.