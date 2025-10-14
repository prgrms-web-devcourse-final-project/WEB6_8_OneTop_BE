-- ==============================================
-- 유니크 인덱스 제거
-- ==============================================
ALTER TABLE decision_nodes DROP CONSTRAINT IF EXISTS uq_decision_line_age;