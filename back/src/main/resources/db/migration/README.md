# Flyway 마이그레이션 가이드

## 개요
이 디렉토리는 Flyway 데이터베이스 마이그레이션 파일을 관리합니다.

## 파일 구조
```
db/migration/
├── V1__init_schema.sql              # 초기 스키마 생성
├── V2__init_prod_essential.sql      # 프로덕션 필수 데이터 (admin 계정)
└── README.md                        # 이 파일
```

## 마이그레이션 파일 명명 규칙
- **형식**: `V{버전}__{설명}.sql`
- **예시**: `V1__init_schema.sql`, `V2__add_user_table.sql`
- **주의**: 언더스코어 2개 (`__`) 사용 필수

## V2__init_prod_essential.sql 사용 전 필수 작업

### 1. BCrypt 패스워드 생성
```bash
# 테스트 실행
./gradlew test --tests "com.back.util.PasswordEncoderTest"

# 콘솔에서 출력된 BCrypt 해시 복사
# 예: $2a$10$N9qo8uLOickgx2ZMRZoMye...
```

### 2. SQL 파일 수정
`V2__init_prod_essential.sql` 파일의 33번째 줄을 실제 BCrypt 해시로 교체:
```sql
-- 수정 전
'$2a$10$YourActualBCryptHashHere',  -- TODO: 실제 BCrypt 해시로 교체

-- 수정 후 (실제 값으로)
'$2a$10$N9qo8uLOickgx2ZMRZoMye...',  -- PasswordEncoderTest 실행 결과
```

## 프로파일별 동작

### dev / test 프로파일
- Flyway: **비활성화** (`enabled: false`)
- InitData.java: **활성화** (풍부한 테스트 데이터 자동 생성)

### prod 프로파일
- Flyway: **활성화** (`enabled: true`)
- InitData.java: **비활성화** (`@Profile("!prod")`)

## 멱등성 보장
모든 마이그레이션 파일은 `ON CONFLICT DO NOTHING` 구문을 사용하여:
- Blue-Green 배포 시 중복 실행 방지
- 재배포 시 안전성 보장
- 429 API 에러 방지

## 실행 이력 확인
```sql
SELECT * FROM flyway_schema_history ORDER BY installed_rank;
```

## 주의사항
1. **마이그레이션 파일은 절대 수정 금지** (체크섬 검증 실패)
2. **새로운 변경사항은 새 파일로 추가** (V3, V4...)
3. **프로덕션 배포 전 반드시 로컬에서 테스트**

## 문제 해결

### 체크섬 오류 발생 시
```sql
-- 마이그레이션 이력 초기화 (주의: 개발 환경에서만!)
DELETE FROM flyway_schema_history WHERE version = '2';
```

### 마이그레이션 실패 시
```bash
# Flyway repair 명령 (Spring Boot Actuator 필요)
./gradlew flywayRepair
```

## 참고 문서
- [Flyway Documentation](https://documentation.red-gate.com/fd)
- [프로젝트 CLAUDE.md](../../../CLAUDE.md)
