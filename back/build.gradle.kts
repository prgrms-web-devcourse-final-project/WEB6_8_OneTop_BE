plugins {
    java
    id("org.springframework.boot") version "3.5.5"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com"
version = "0.0.1-SNAPSHOT"
description = "back"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client") // OAuth2 Client 추가

    // Redis
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // Embedded Redis
    implementation("com.github.codemonstur:embedded-redis:1.4.3")

    // Session
    implementation("org.springframework.session:spring-session-data-redis")

    // Health Check
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    implementation("org.testcontainers:testcontainers")
    implementation("org.testcontainers:jdbc")
    implementation("org.testcontainers:postgresql")

    // Swagger
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Database
    runtimeOnly("com.h2database:h2")
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.postgresql:postgresql")

    // Migration
     implementation("org.flywaydb:flyway-core:11.11.2")
     runtimeOnly("org.flywaydb:flyway-database-postgresql:11.11.2")

    // QueryDSL
    implementation("io.github.openfeign.querydsl:querydsl-jpa:7.0")
    annotationProcessor("io.github.openfeign.querydsl:querydsl-apt:7.0:jpa")
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")
    testAnnotationProcessor("io.github.openfeign.querydsl:querydsl-apt:7.0:jpa")
    testAnnotationProcessor("jakarta.persistence:jakarta.persistence-api")

    // AI Services - WebFlux for non-blocking HTTP clients
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("io.netty:netty-tcnative-boringssl-static:2.0.65.Final")


    // AWS SDK for S3
    implementation("software.amazon.awssdk:s3:2.20.+")

    // macOS Netty 네이티브 DNS 리졸버 (WebFlux 필요)
    val isMacOS: Boolean = System.getProperty("os.name").startsWith("Mac OS X")
    val architecture = System.getProperty("os.arch").lowercase()
    if (isMacOS && architecture == "aarch64") {
        developmentOnly("io.netty:netty-resolver-dns-native-macos:4.1.68.Final:osx-aarch_64")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
