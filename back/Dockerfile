#####################################################
# 마지막 수정: 250924
# 작성자: gooraeng
#
# CD 과정에서 사용될 Dockerfile 입니다.
# 멀티 스테이지 적용 (빌드 스테이지, 실행 스테이지)
#####################################################

######### 빌드 스테이지 시작 #########
FROM gradle:jdk-21-and-23-graal-jammy AS builder

# 작업 디렉토리 설정
WORKDIR /app

# Gradle build, Setting 파일 복사
COPY build.gradle.kts settings.gradle.kts gradlew ./
COPY gradle gradle

# Gradlew에 실행권한 부여
RUN chmod +x gradlew

# 종속성 설치
RUN ./gradlew dependencies --no-daemon

# 소스 코드 복사
COPY src src

# 애플리케이션 빌드 (CI 통과 이후 테스트 불필요)
RUN ./gradlew build -x test --no-daemon --build-cache
######### 빌드 스테이지 끝 #########

######### 실행 스테이지 시작 #########
FROM container-registry.oracle.com/graalvm/jdk:21

# 작업 디렉토리 설정
WORKDIR /app

# 첫 번째(빌드) 스테이지에서 빌드된 JAR 파일 복사
COPY --from=builder /app/build/libs/*.jar app.jar

# JVM 메모리 설정
# - XX:MaxRAMPercentage=N
# : 컨테이너 메모리의 N(%)를 최대 힙 메모리로 사용
# - Xss(N)k
# : 스레드 스택 크기를 N(KB)로 설정 (기본값은 1MB, 스레드 수가 많을 경우 메모리 부족 방지 목적)
# (주의: 너무 낮게 설정 시 StackOverflowError 발생 가능)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=65 -Xss512k"

# 실행할 JAR 파일 지정
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dspring.profiles.active=prod -jar app.jar"]
######### 실행 스테이지 끝 #########