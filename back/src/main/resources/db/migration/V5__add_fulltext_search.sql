-- ============================================
-- PostgreSQL Full-Text Search 마이그레이션
-- ============================================

-- 1. 카테고리 인덱스, 최신순 정렬을 위한 인덱스 추가
CREATE INDEX idx_post_category ON post(category);
CREATE INDEX idx_post_created_desc ON post(created_date DESC);

-- 2. 기존 제목 인덱스 - 풀텍스트 서치 영향 미치지 않으므로 제거
DROP INDEX IF EXISTS idx_post_title;

-- 3. 검색 컬럼 추가
ALTER TABLE post ADD COLUMN search_vector tsvector;

-- 4. GIN 인덱스 생성
CREATE INDEX idx_post_search_vector ON post USING GIN (search_vector);

-- 5. 자동 업데이트 함수 + 트리거
CREATE OR REPLACE FUNCTION post_search_vector_update()
RETURNS TRIGGER AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('simple', COALESCE(NEW.title, '')), 'A') ||
        setweight(to_tsvector('simple', COALESCE(NEW.content, '')), 'B');
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tsvector_update
    BEFORE INSERT OR UPDATE OF title, content ON post
    FOR EACH ROW EXECUTE FUNCTION post_search_vector_update();

-- 6. 기존 데이터 업데이트
UPDATE post SET search_vector =
                    setweight(to_tsvector('simple', COALESCE(title, '')), 'A') ||
                    setweight(to_tsvector('simple', COALESCE(content, '')), 'B')
WHERE search_vector IS NULL;