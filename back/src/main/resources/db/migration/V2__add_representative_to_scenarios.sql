-- ========================================
-- 시나리오 대표 여부 컬럼 추가
-- ========================================
ALTER TABLE scenarios ADD COLUMN representative BOOLEAN DEFAULT FALSE NOT NULL;
