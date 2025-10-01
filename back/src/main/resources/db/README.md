# Flyway Migration Guide

## 파일 명명 규칙

```
- resources/db/migration 에서 작업

V{version}__{description}.sql
```

- **V**: 버전 마이그레이션 (필수)
- **version**: 숫자 (예: 1, 2, 1.1, 2023.01.01)
- **__**: 언더스코어 2개로 구분
- **description**: 영문 설명 (snake_case)

### 예시
```
V1__init_schema.sql
V2__add_user_table.sql
V3__add_post_indexes.sql
V3.1__fix_user_email_constraint.sql
```

## 마이그레이션 작성 예시

### V1__init_schema.sql
```sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    nickname VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);
```

### V2__add_posts.sql
```sql
CREATE TABLE posts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
```

## 주의사항

1. **절대 수정 금지**: 이미 적용된 마이그레이션 파일은 수정하지 말 것
2. **롤백 없음**: Flyway는 자동 롤백을 지원하지 않음 (수동으로 새 마이그레이션 작성)
3. **순서 보장**: 버전 번호 순서대로 실행됨
4. **Production 필수**: `application-prod.yml`에서 `flyway.enabled: true`

## 현재 설정

- **Location**: `classpath:db/migration`
- **Baseline**: `0`
- **개발환경**: Flyway 비활성화 (H2 자동 스키마)
- **운영환경**: Flyway 활성화 (PostgreSQL)
