-- pgvector 확장
CREATE EXTENSION IF NOT EXISTS vector;

-- 임베딩 차원은 실제 모델에 맞추세요 (예: 768/1024/1536...)
CREATE TABLE IF NOT EXISTS node_snippet (
                                            id        BIGSERIAL PRIMARY KEY,
                                            line_id   BIGINT NOT NULL,
                                            age_year  INT    NOT NULL,
                                            text      TEXT,
                                            embedding VECTOR(768)
    );

CREATE INDEX IF NOT EXISTS ix_node_snippet_line_age
    ON node_snippet(line_id, age_year);

-- 코사인 거리 KNN 인덱스
CREATE INDEX IF NOT EXISTS ix_node_snippet_embedding
    ON node_snippet USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
