package com.back.global.config;

import com.back.global.jackson.ProblemDetailJsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ProblemDetail;

/**
 * JSON 처리를 위한 ObjectMapper 설정 클래스.
 * Jackson 라이브러리를 사용하여 Java 객체와 JSON 간 변환을 담당합니다.
 */
@Configuration
public class JsonConfig {

    /**
     * ObjectMapper Bean 등록.
     * 전체 프로젝트에서 JSON 파싱에 사용할 공통 인스턴스입니다.
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Java 8 시간 타입 지원 (LocalDateTime 등)
        mapper.registerModule(new JavaTimeModule());

        SimpleModule pdModule = new SimpleModule();
        pdModule.addSerializer(ProblemDetail.class, new ProblemDetailJsonSerializer());
        mapper.registerModule(pdModule);

        // 알려지지 않은 속성이 있어도 JSON 파싱 실패하지 않음 (알려지지 않은 속성 무시)
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // 날짜를 문자열로 변환 (타임스탬프 대신)
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        return mapper;
    }
}