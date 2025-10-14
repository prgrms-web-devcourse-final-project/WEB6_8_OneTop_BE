-- [코드 흐름 요약]
-- 1) pgvector 확장을 설치한다.
-- 2) Hibernate가 이후에 vector(768) 컬럼을 포함한 테이블을 생성할 수 있게 준비한다.
CREATE EXTENSION IF NOT EXISTS vector;
