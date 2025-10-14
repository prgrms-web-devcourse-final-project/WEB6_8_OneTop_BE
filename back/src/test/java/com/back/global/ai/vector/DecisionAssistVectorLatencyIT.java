/*
 * [코드 흐름 요약]
 * 1) 테스트 컨텍스트에서 AIVectorService를 실구현으로 강제 오버라이드(@Primary)
 * 2) AiLatencyProbeConfig로 Gemini HTTP 왕복 시간(ms) 계측
 * 3) budget.reset(1)로 1회 실호출 강제, 호출 후 realAi=true + e2e/http 시간 출력
 */
package com.back.global.ai.vector;

import com.back.domain.node.controller.AiCallBudget;
import com.back.domain.node.controller.AiOnceDelegateTestConfig;
import com.back.domain.node.entity.DecisionNode;
import com.back.domain.search.entity.NodeSnippet;
import com.back.domain.search.repository.NodeSnippetRepository;
import com.back.global.ai.client.text.TextAiClient;
import com.back.global.ai.config.SituationAiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@TestPropertySource(properties = {
        "app.initdata.enabled=false"
})

@ActiveProfiles("test") // 스텁이 떠 있어도 아래 @Primary 오버라이드가 이긴다
@Import({DecisionAssistVectorLatencyIT.RealAiOverrideConfig.class, AiLatencyProbeConfig.class,
AiOnceDelegateTestConfig.class})
class DecisionAssistVectorLatencyIT {

    // Docker 선기동 + pgvector
    static final boolean DOCKER = initDocker();
    static boolean initDocker() {
        try { return DockerClientFactory.instance().isDockerAvailable(); } catch (Throwable t) { return false; }
    }
    static final PostgreSQLContainer<?> PG =
            new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("relife_test")
                    .withUsername("test")
                    .withPassword("test")
                    .withInitScript("sql/init_vector.sql");
    static { if (DOCKER) PG.start(); }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        Assumptions.assumeTrue(DOCKER, "Docker not available — skipping");
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", PG::getUsername);
        r.add("spring.datasource.password", PG::getPassword);
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "update");
        r.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");

        // 실키 사용 — 환경변수 반드시 세팅: GEMINI_API_KEY
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getProperty("GEMINI_API_KEY");
        }
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(), "GEMINI_API_KEY not set — skipping");
        String finalApiKey = apiKey;
        r.add("ai.text.gemini.api-key", () -> finalApiKey);
        r.add("ai.text.gemini.model", () -> "gemini-2.0-flash");
        r.add("ai.text.gemini.base-url", () -> "https://generativelanguage.googleapis.com");
    }

    @Autowired EmbeddingClient embeddingClient;
    @Autowired PgVectorSearchService vectorSearch;
    @Autowired AIVectorService aivectorService;   // 오버라이드된 실구현 주입
    @Autowired DecisionAssistVectorFlowITTestOps ops; // 단순 세이브 헬퍼

    @Autowired
    AiCallBudget budget; // 예산 주입

    @BeforeEach
    void setup() {
        ops.clearSnippets();
    }

    @Test
    @DisplayName("실제 Gemini 호출 레이턴시 측정(E2E / HTTP)")
    void latency_real_ai() {
        // 1) 실호출 1회 강제
        budget.reset(1);

        // 2) RAG 콘텍스트 준비
        long lineId = 900L; int age = 22;
        ops.saveSnippet(lineId, age, "수도권 컴공 진학 비용과 통학 고민", "EDUCATION");
        ops.saveSnippet(lineId, age, "서울 스타트업 인턴 이력서 준비", "CAREER");

        // 3) 호출
        long t0 = System.nanoTime();
        DecisionNode last = DecisionNode.builder().ageYear(age).situation("컴공 고려").decision("컴공 선택").build();
        var hint = aivectorService.generateNextHint(1L, lineId, List.of(last));
        long e2eMs = (System.nanoTime() - t0) / 1_000_000;

        long httpMs = AiLatencyProbeConfig.LAST_LATENCY_MS.get();
        boolean realAi = httpMs >= 0; // 데코레이터가 시간 기록했으면 실호출

        System.out.println("[LAT] realAi=" + realAi + " e2eMs=" + e2eMs + " httpMs=" + httpMs +
                " situation=" + hint.aiNextSituation() + " option=" + hint.aiNextRecommendedOption());

        assertThat(realAi).isTrue();     // 반드시 실호출이어야 함
        assertThat(hint.aiNextSituation()).isNotBlank();
    }

    @Test
    @DisplayName("워밍업 후 레이턴시 P50 검증(상황 생성 경로)")
    void latency_after_warmup_p50() {
        // next 노드 생성: 워밍업 1회(최소 출력·짧은 프롬프트 권장)
        budget.reset(1); // 무결성 검증
        aivectorService.generateNextHint(1L, 900L, List.of(
                DecisionNode.builder().ageYear(22).situation("컴공 고려").decision("컴공 선택").build()
        ));

        // 측정 N회
        int N = 5;
        long[] e2e = new long[N];
        long[] http = new long[N];
        for (int i = 0; i < N; i++) {
            budget.reset(1); // 무결성 검증
            long t0 = System.nanoTime();
            var hint = aivectorService.generateNextHint(1L, 900L, List.of(
                    DecisionNode.builder().ageYear(22).situation("컴공 고려").decision("컴공 선택").build()
            ));
            long e2eMs = (System.nanoTime() - t0) / 1_000_000;
            long httpMs = AiLatencyProbeConfig.LAST_LATENCY_MS.get();
            e2e[i] = e2eMs;
            http[i] = httpMs;
        }

        Arrays.sort(e2e);
        Arrays.sort(http);
        long p50E2E = e2e[N/2];
        long p50HTTP = http[N/2];
        System.out.println("[P50] e2e=" + p50E2E + "ms, http=" + p50HTTP + "ms");

        // 무결성 검증: 목표 상한(예: 1200ms)을 테스트 기준으로 잡아두기
        assertThat(p50HTTP).isLessThan(1200);
    }

    // --- 테스트용 헬퍼/오버라이드 ---

    @TestConfiguration
    static class DecisionAssistVectorFlowITTestOps {
        private final NodeSnippetRepository repo;
        private final EmbeddingClient emb;
        DecisionAssistVectorFlowITTestOps(NodeSnippetRepository repo, EmbeddingClient emb) {
            this.repo = repo; this.emb = emb;
        }
        // 가장 많이 사용하는 함수 호출 위에 한줄로만
        void clearSnippets() { repo.deleteAllInBatch(); }
        // 가장 중요한 함수 위에 한줄로만
        void saveSnippet(Long lineId, int age, String text, String category) {
            repo.save(NodeSnippet.builder()
                    .lineId(lineId).ageYear(age).category(category)
                    .text(text).embedding(emb.embed(text)).updatedAt(LocalDateTime.now())
                    .build());
        }
    }

    @TestConfiguration
    static class RealAiOverrideConfig {
        // 한줄 요약: AIVectorService를 실구현으로 강제(@Primary)해서 test 스텁을 덮어쓴다
        @Bean
        AIVectorService realAIVectorService(
                TextAiClient textAiClient,
                AIVectorServiceSupportDomain support,
                SituationAiProperties props,
                ObjectMapper objectMapper
        ) {
            var impl = new AIVectorServiceImpl(textAiClient, support, props, objectMapper);
            impl.setTopK(1); impl.setContextCharLimit(160); impl.setMaxOutputTokens(48);
            return impl;
        }

    }
}
