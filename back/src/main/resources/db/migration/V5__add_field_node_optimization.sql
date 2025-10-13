-- ==============================================
-- DecisionLine 및 DecisionNode 테이블 필드 추가
-- ==============================================

-- DecisionLine에 parent_line_id 필드 추가
ALTER TABLE decision_lines ADD COLUMN IF NOT EXISTS parent_line_id BIGINT;

-- DecisionNode에 situation 관련 필드 추가
ALTER TABLE decision_nodes ADD COLUMN IF NOT EXISTS ai_next_situation TEXT;
ALTER TABLE decision_nodes ADD COLUMN IF NOT EXISTS ai_next_recommended_option TEXT;