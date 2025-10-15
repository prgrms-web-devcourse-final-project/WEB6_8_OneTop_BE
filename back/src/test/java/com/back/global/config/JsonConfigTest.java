package com.back.global.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * JsonConfig 설정과 ObjectMapper 동작을 테스트합니다.
 * Spring Boot 통합 테스트로 실제 Bean이 정상적으로 동작하는지 검증합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.profiles.active=test")
@DisplayName("JsonConfig 및 ObjectMapper 테스트")
class JsonConfigTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Nested
    @DisplayName("JSON 파싱 기능")
    class JsonParsingTests {

        @Test
        @DisplayName("성공 - Map<String, String> 타입으로 JSON 파싱")
        void parseJson_성공_맵타입으로_JSON_파싱() throws JsonProcessingException {
            // Given
            String json = "{\"2020\":\"창업 시작\",\"2025\":\"상장 성공\",\"2030\":\"글로벌 진출\"}";

            // When
            Map<String, String> result = objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});

            // Then
            assertThat(result).isNotNull();
            assertThat(result).hasSize(3);
            assertThat(result.get("2020")).isEqualTo("창업 시작");
            assertThat(result.get("2025")).isEqualTo("상장 성공");
            assertThat(result.get("2030")).isEqualTo("글로벌 진출");
        }

        @Test
        @DisplayName("성공 - 빈 JSON 객체 파싱")
        void parseJson_성공_빈_JSON_객체_파싱() throws JsonProcessingException {
            // Given
            String emptyJson = "{}";

            // When
            Map<String, String> result = objectMapper.readValue(emptyJson, new TypeReference<Map<String, String>>() {});

            // Then
            assertThat(result).isNotNull();
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("성공 - 한글 포함 JSON 파싱")
        void parseJson_성공_한글포함_JSON_파싱() throws JsonProcessingException {
            // Given
            String koreanJson = "{\"직업\":\"개발자\",\"취미\":\"독서\",\"목표\":\"성장하는 개발자\"}";

            // When
            Map<String, String> result = objectMapper.readValue(koreanJson, new TypeReference<Map<String, String>>() {});

            // Then
            assertThat(result).isNotNull();
            assertThat(result.get("직업")).isEqualTo("개발자");
            assertThat(result.get("취미")).isEqualTo("독서");
            assertThat(result.get("목표")).isEqualTo("성장하는 개발자");
        }

        @Test
        @DisplayName("실패 - 잘못된 JSON 형식")
        void parseJson_실패_잘못된_JSON_형식() {
            // Given
            String invalidJson = "{\"key\":\"value\",}"; // 마지막 콤마로 인한 잘못된 JSON

            // When & Then
            assertThatThrownBy(() -> objectMapper.readValue(invalidJson, new TypeReference<Map<String, String>>() {}))
                    .isInstanceOf(JsonProcessingException.class);
        }
    }

    @Nested
    @DisplayName("JSON 직렬화 기능")
    class JsonSerializationTests {

        @Test
        @DisplayName("성공 - Map을 JSON으로 직렬화")
        void serializeToJson_성공_맵을_JSON으로_직렬화() throws JsonProcessingException {
            // Given
            Map<String, String> data = Map.of(
                    "2020", "대학 졸업",
                    "2023", "첫 직장 입사",
                    "2025", "팀 리더 승진"
            );

            // When
            String json = objectMapper.writeValueAsString(data);

            // Then
            assertThat(json).isNotNull();
            assertThat(json).contains("\"2020\":\"대학 졸업\"");
            assertThat(json).contains("\"2023\":\"첫 직장 입사\"");
            assertThat(json).contains("\"2025\":\"팀 리더 승진\"");
        }
    }

    @Nested
    @DisplayName("Java 8 시간 타입 지원")
    class JavaTimeModuleTests {

        @Test
        @DisplayName("성공 - LocalDateTime 직렬화 및 역직렬화")
        void localDateTime_성공_직렬화_역직렬화() throws JsonProcessingException {
            // Given
            TestTimeObject original = new TestTimeObject();
            original.createdAt = LocalDateTime.of(2024, 9, 17, 14, 30, 0);

            // When - 직렬화
            String json = objectMapper.writeValueAsString(original);

            // Then - JSON이 타임스탬프가 아닌 문자열 형태인지 확인
            assertThat(json).contains("2024-09-17T14:30:00");
            assertThat(json).doesNotContain("1726574200"); // 타임스탬프 형식이 아님

            // When - 역직렬화
            TestTimeObject deserialized = objectMapper.readValue(json, TestTimeObject.class);

            // Then
            assertThat(deserialized.createdAt).isEqualTo(original.createdAt);
        }

        private static class TestTimeObject {
            public LocalDateTime createdAt;
        }
    }

    @Nested
    @DisplayName("알려지지 않은 속성 처리")
    class UnknownPropertiesTests {

        @Test
        @DisplayName("성공 - 알려지지 않은 속성이 있어도 파싱 성공")
        void unknownProperties_성공_알려지지않은_속성_무시() throws JsonProcessingException {
            // Given
            String jsonWithUnknownProperty = """
                {
                    "name": "홍길동",
                    "age": 25,
                    "unknownField": "무시될 필드",
                    "anotherUnknown": 12345
                }
                """;

            // When & Then - 예외가 발생하지 않아야 함
            assertThatCode(() -> {
                TestSimpleObject result = objectMapper.readValue(jsonWithUnknownProperty, TestSimpleObject.class);
                assertThat(result.name).isEqualTo("홍길동");
                assertThat(result.age).isEqualTo(25);
            }).doesNotThrowAnyException();
        }

        private static class TestSimpleObject {
            public String name;
            public int age;
        }
    }

    @Nested
    @DisplayName("ObjectMapper Bean 설정 검증")
    class BeanConfigurationTests {

        @Test
        @DisplayName("성공 - ObjectMapper Bean이 정상적으로 주입됨")
        void objectMapperBean_성공_정상적으로_주입됨() {
            // Given & When & Then
            assertThat(objectMapper).isNotNull();
            assertThat(objectMapper).isInstanceOf(ObjectMapper.class);
        }

        @Test
        @DisplayName("성공 - JsonConfig 설정이 적용됨")
        void jsonConfig_성공_설정이_적용됨() {
            // Given & When & Then
            // FAIL_ON_UNKNOWN_PROPERTIES가 false로 설정되었는지 확인
            assertThat(objectMapper.getDeserializationConfig().isEnabled(
                    com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES))
                    .isFalse();

            // WRITE_DATES_AS_TIMESTAMPS가 비활성화되었는지 확인
            assertThat(objectMapper.getSerializationConfig().isEnabled(
                    com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
                    .isFalse();
        }
    }
}