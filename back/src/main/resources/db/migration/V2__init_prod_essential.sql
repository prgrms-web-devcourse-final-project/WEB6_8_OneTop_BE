--liquibase formatted sql
--dialect postgresql

-- ============================================
-- V2__init_prod_essential.sql
-- 프로덕션 환경 필수 초기 데이터
-- ============================================
-- 설명:
--   - Blue-Green 배포 시에도 멱등성을 보장합니다
--   - ON CONFLICT DO NOTHING 구문으로 중복 실행 방지
--   - admin 계정만 생성 (테스트 데이터는 제외)
-- ============================================

-- Admin 사용자 생성 (멱등하게)
INSERT INTO users (
    email,
    password,
    role,
    username,
    nickname,
    birthday_at,
    gender,
    mbti,
    beliefs,
    created_date,
    updated_date
)
VALUES (
    'admin@example.com',
    -- 주의: 실제 프로덕션에서는 PasswordEncoderTest 실행 결과로 교체하세요
    -- 현재 값은 'admin1234!' 인코딩 결과 (매번 다를 수 있음)
    '$2a$10$3d.SEWYbRSu6Wett4Txv9OxIL2AccoJP76Hcja5b/T.e4nB9nluIS',
    'ADMIN',
    '관리자',
    '관리자',
    '1990-01-01 00:00:00',
    'M',
    'INTJ',
    '합리주의',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (email) DO NOTHING;

-- 초기화 완료 로그
-- Flyway는 실행 성공 시 flyway_schema_history 테이블에 자동 기록됩니다.
