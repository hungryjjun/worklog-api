-- worklog DB에서 수동 실행용, 애플리케이션이 자동 실행하지 않음

CREATE TABLE `attach_file` (
  `atch_file_id` bigint NOT NULL AUTO_INCREMENT COMMENT '첨부파일 그룹 ID',
  `se_cd` varchar(20) NOT NULL COMMENT '업무구분코드 (NOTICE 등)',
  `use_yn` char(1) NOT NULL DEFAULT 'Y' COMMENT '사용여부',
  `create_emp_no` varchar(20) NOT NULL COMMENT '생성자 사번',
  `create_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
  PRIMARY KEY (`atch_file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='첨부파일';

CREATE TABLE `attach_file_detail` (
  `atch_file_id` bigint NOT NULL COMMENT '첨부파일 그룹 ID',
  `file_sn` int NOT NULL COMMENT '파일 순번',
  `origin_name` varchar(255) NOT NULL COMMENT '원본 파일명',
  `stored_name` varchar(255) NOT NULL COMMENT '서버 저장 파일명',
  `file_ext` varchar(20) DEFAULT NULL COMMENT '확장자',
  `file_size` bigint NOT NULL COMMENT '크기(byte)',
  `create_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
  PRIMARY KEY (`atch_file_id`,`file_sn`),
  CONSTRAINT `fk_attach_file_detail` FOREIGN KEY (`atch_file_id`) REFERENCES `attach_file` (`atch_file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='첨부파일 상세';

CREATE TABLE `notice` (
  `notice_seq` bigint NOT NULL AUTO_INCREMENT COMMENT '순번',
  `category` varchar(20) NOT NULL DEFAULT 'GENERAL' COMMENT '구분',
  `title` varchar(200) NOT NULL COMMENT '제목',
  `content` text NOT NULL COMMENT '내용',
  `atch_file_id` bigint DEFAULT NULL COMMENT '첨부파일 그룹 ID',
  `view_cnt` int NOT NULL DEFAULT '0' COMMENT '조회수',
  `del_yn` char(1) NOT NULL DEFAULT 'N' COMMENT '삭제여부',
  `create_emp_no` varchar(20) NOT NULL COMMENT '생성자 사번',
  `create_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
  `update_emp_no` varchar(20) DEFAULT NULL COMMENT '수정자 사번',
  `update_at` datetime DEFAULT NULL COMMENT '수정일시',
  PRIMARY KEY (`notice_seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='공지사항';
