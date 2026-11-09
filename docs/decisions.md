# 설계 결정

## 첨부파일

### 구조

- 첨부파일은 공통 그룹 구조로 관리한다 (`attach_file` 1 : N `attach_file_detail`).
- 업무 테이블은 `atch_file_id`만 보유하고, 업무 구분은 `attach_file.se_cd`로 관리한다.
- `notice.atch_file_id`에는 FK를 걸지 않는다. 공통 구조이므로 애플리케이션에서 관리한다.

### 삭제와 정리

- 공지 삭제는 `del_yn` 소프트 삭제로 처리하고, 이때 연결된 `attach_file.use_yn`을 `'N'`으로 변경한다.
- 고아 파일은 `use_yn = 'N'` 기준으로 배치 정리할 예정이다.
