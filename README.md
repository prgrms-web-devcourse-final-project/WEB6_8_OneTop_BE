# Re:Life — AI 평행우주 시뮬레이션
<br>

[`Re:Life 서비스 링크`](https://www.relife.kr/)

<br>

## 개요
1. [소개](#소개)
2. [기술스택](#기술스택)
3. [빌드 및 사용법](#빌드-및-사용법)
4. [문의](#문의)

<br>

## 소개
- 프로그래머스 웹개발 6기 8회차 1팀(OneTop)의 **백엔드 최종 프로젝트** 레포지토리입니다.<br>
- **Re:Life**는 사용자의 중요한 인생 선택을 기록하고, AI와 실제 통계를 바탕으로 "그때 다른 선택을 했더라면?" 에 대한 **평행우주 시나리오**를 생성·비교할 수 있는 플랫폼입니다.

<br>

## 기술스택
### 백엔드
- [`Spring Boot (Java 21)`](https://spring.io/)
- [`Spring Security`]
- [`OAuth2.0 (Google/GitHub)`]
- [`Spring Data JPA`]
- [`Spring Session (Redis)`]
- [`Redis`]
- [`JWT (jjwt)`]
- [`Springdoc OpenAPI (Swagger UI)`]
- [`QueryDSL`]
- [`Flyway`]
- [`Actuator`]
- [`REST API`]
- [`H2`]
- [`PostgreSQL`]
- [`AWS SDK for S3`]

### 프론트 엔드
- [`TypeScript`]
- [`Next.js`](https://nextjs.org/)
- [`Tailwind CSS`](https://tailwindcss.com/)

### 배포
- [`AWS (S3 / CloudFront)`](https://aws.amazon.com/ko/)

<br>

## 빌드 및 사용법
### 서비스 접속
- 실제 서비스는 아래 URL에서 접속 가능합니다.<br>
  [https://www.relife.kr](https://www.relife.kr)

### 로컬 빌드 및 실행 — Backend
1. 저장소를 클론합니다.
   <br>ex) `git clone <this-repo-url> .`

2. 빌드/실행
#### Unix / macOS
```bash
cd back

# 1. Clean \& Build
./gradlew clean build

# 2. Run the JAR
java -jar build/libs/\*.jar

# 3. (선택) 개발 모드로 바로 실행
./gradlew bootRun
```

#### Windows

``` bash
cd back

# 1. Clean \& Build
gradlew.bat clean build

# 2. Run the JAR
java -jar build\libs\*.jar

# 3. 개발 모드로 바로 실행
gradlew.bat bootRun

프록시/정적리소스 서버는 팀 환경에 맞게 설정 파일을 사용
```
### 주의사항 (Backend 환경변수)
백엔드 설정파일에 환경변수가 필요
<br>
back/.env.local

### env 파일
```bash (env) 
AWS_REGION=SHOULD_BE_SET_IF_YOU_USE_AWS_DEPENDENCIES
AWS_ACCESS_KEY_ID=SHOULD_BE_SET_IF_YOU_USE_AWS_DEPENDENCIES
AWS_SECRET_ACCESS_KEY=SHOULD_BE_SET_IF_YOU_USE_AWS_DEPENDENCIES
AWS_CLOUD_FRONT_DOMAIN=SHOULD_BE_SET_IF_YOU_USE_AWS_DEPENDENCIES
AWS_S3_BUCKET_NAME=SHOULD_BE_SET_IF_YOU_USE_AWS_DEPENDENCIES
PROD_BASE_DOMAIN=localhost

GOOGLE_CLIENT_ID=MUST_BE_SET_AT_LEAST
GOOGLE_CLIENT_SECRET=MUST_BE_SET_AT_LEAST
GITHUB_CLIENT_ID=MUST_BE_SET_AT_LEAST
GITHUB_CLIENT_SECRET=MUST_BE_SET_AT_LEAST

GEMINI_API_KEY=MUST_BE_SET_AT_LEAST

```

문의
백엔드 팀원 정보 및 역할

<br>
김영건(PO)
<br>email : johnbosco0414@gmail.com

이찬수(백엔드 팀장)
<br>email : l65783082@gmail.com

김지훈(인프라/배포)
<br>email : birdyoon1998@gmail.com

오현배(커뮤니티)
<br>email : shihan005@gmail.com

임정민(회원관리 및 보안)
<br>email : imjeongmin587@gmail.com