-- ========================================
-- Scenario 테이블 인덱스 및 제약조건 정리
-- ========================================

-- 기존 V1의 constraint 이름 변경 (uq_scenario_decision_line -> uk_scenario_decision_line)
ALTER TABLE scenarios DROP CONSTRAINT IF EXISTS uq_scenario_decision_line;
ALTER TABLE scenarios ADD CONSTRAINT uk_scenario_decision_line UNIQUE (decision_line_id);

-- 인덱스 추가
CREATE INDEX IF NOT EXISTS idx_scenario_user_status ON scenarios (user_id, status, created_date);
CREATE INDEX IF NOT EXISTS idx_scenario_baseline ON scenarios (base_line_id);