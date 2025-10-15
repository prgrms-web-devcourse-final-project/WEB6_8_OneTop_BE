///*
// * [코드 흐름 요약]
// * 1) 헤더 1개(parent==null) + 본문 3개(체인) = 총 4개의 DecisionNode 생성 유틸 추가
// * 2) 테스트에서 단일 노드 대신 전체 체인을 전달하여 dropHeader 이후에도 본문이 남도록 보장
// * 3) 마지막 본문 나이를 22로 맞춰 RAG 검색(age=22)과 정합성 유지
// */
//package com.back.global.ai.vector;
//
//import com.back.domain.node.controller.AiCallBudget;
//import com.back.domain.node.controller.AiOnceDelegateTestConfig;
//import com.back.domain.node.entity.DecisionNode;
//import com.back.domain.search.entity.NodeSnippet;
//import com.back.domain.search.repository.NodeSnippetRepository;
//import org.junit.jupiter.api.Assumptions;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.boot.test.context.TestConfiguration;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Import;
//import org.springframework.test.context.ActiveProfiles;
//import org.springframework.test.context.DynamicPropertyRegistry;
//import org.springframework.test.context.DynamicPropertySource;
//import org.springframework.test.context.TestPropertySource;
//import org.testcontainers.DockerClientFactory;
//import org.testcontainers.containers.PostgreSQLContainer;
//import org.testcontainers.junit.jupiter.Testcontainers;
//import org.testcontainers.utility.DockerImageName;
//
//import java.util.Arrays;
//import java.util.ArrayList;
//import java.util.List;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//@Testcontainers(disabledWithoutDocker = true)
//@SpringBootTest
//@TestPropertySource(properties = {
//        "app.initdata.enabled=false"
//})
//@ActiveProfiles("test-pg")
//@Import({AiLatencyProbeConfig.class, AiOnceDelegateTestConfig.class})
//class DecisionAssistVectorLatencyIT {
//
//    // Docker 선기동 + pgvector
//    static final boolean DOCKER = initDocker();
//    static boolean initDocker() {
//        try { return DockerClientFactory.instance().isDockerAvailable(); } catch (Throwable t) { return false; }
//    }
//    static final PostgreSQLContainer<?> PG =
//            new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16")
//                    .asCompatibleSubstituteFor("postgres"))
//                    .withDatabaseName("relife_test")
//                    .withUsername("test")
//                    .withPassword("test")
//                    .withInitScript("sql/init_vector.sql");
//    static { if (DOCKER) PG.start(); }
//
//    @DynamicPropertySource
//    static void props(DynamicPropertyRegistry r) {
//        Assumptions.assumeTrue(DOCKER, "Docker not available — skipping");
//        r.add("spring.datasource.url", PG::getJdbcUrl);
//        r.add("spring.datasource.username", PG::getUsername);
//        r.add("spring.datasource.password", PG::getPassword);
//        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
//        r.add("spring.jpa.hibernate.ddl-auto", () -> "update");
//        r.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
//
//        // 실키 사용 — 환경변수 반드시 세팅: GEMINI_API_KEY
//        String apiKey = System.getenv("GEMINI_API_KEY");
//        if (apiKey == null || apiKey.isBlank()) {
//            apiKey = System.getProperty("GEMINI_API_KEY");
//        }
//        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(), "GEMINI_API_KEY not set — skipping");
//        String finalApiKey = apiKey;
//        r.add("ai.text.gemini.api-key", () -> finalApiKey);
//        r.add("ai.text.gemini.model", () -> "gemini-2.0-flash");
//        r.add("ai.text.gemini.base-url", () -> "https://generativelanguage.googleapis.com");
//    }
//
//    @Autowired EmbeddingClient embeddingClient;
//    @Autowired PgVectorSearchService vectorSearch;
//    @Autowired AIVectorService aivectorService;
//    @Autowired DecisionAssistVectorFlowITTestOps ops;
//
//    @Autowired
//    AiCallBudget budget;
//
//    @BeforeEach
//    void setup() {
//        ops.clearSnippets();
//    }
//
//    @Test
//    @DisplayName("실제 Gemini 호출 레이턴시 측정(E2E / HTTP)")
//    void latency_real_ai() {
//        // next 노드 생성
//        budget.reset(1);
//
//        // RAG 콘텍스트 준비
//        long lineId = 900L;
//        ops.saveSnippet(lineId, 22, "수도권 컴공 진학 비용과 통학 고민", "EDUCATION");
//        ops.saveSnippet(lineId, 22, "서울 스타트업 인턴 이력서 준비", "CAREER");
//
//        // 호출
//        long t0 = System.nanoTime();
//        List<DecisionNode> nodes = buildNodesWithHeader(); // 무결성 검증
//        var hint = aivectorService.generateNextHint(1L, lineId, nodes);
//        long e2eMs = (System.nanoTime() - t0) / 1_000_000;
//
//        long httpMs = AiLatencyProbeConfig.LAST_LATENCY_MS.get();
//        boolean realAi = httpMs >= 0;
//
//        System.out.println("[LAT] realAi=" + realAi + " e2eMs=" + e2eMs + " httpMs=" + httpMs +
//                " situation=" + hint.aiNextSituation() + " option=" + hint.aiNextRecommendedOption());
//
//        assertThat(realAi).isTrue();
//        assertThat(hint.aiNextSituation()).isNotBlank();
//    }
//
//    @Test
//    @DisplayName("워밍업 후 레이턴시 P50 검증(상황 생성 경로)")
//    void latency_after_warmup_p50() {
//        // next 노드 생성
//        budget.reset(1); // 무결성 검증
//        aivectorService.generateNextHint(1L, 900L, buildNodesWithHeader());
//
//        // 측정 N회
//        int N = 5;
//        long[] e2e = new long[N];
//        long[] http = new long[N];
//        for (int i = 0; i < N; i++) {
//            budget.reset(1); // 무결성 검증
//            long t0 = System.nanoTime();
//            var hint = aivectorService.generateNextHint(1L, 900L, buildNodesWithHeader());
//            long e2eMs = (System.nanoTime() - t0) / 1_000_000;
//            long httpMs = AiLatencyProbeConfig.LAST_LATENCY_MS.get();
//            e2e[i] = e2eMs;
//            http[i] = httpMs;
//        }
//
//        Arrays.sort(e2e);
//        Arrays.sort(http);
//        long p50E2E = e2e[N/2];
//        long p50HTTP = http[N/2];
//        System.out.println("[P50] e2e=" + p50E2E + "ms, http=" + p50HTTP + "ms");
//
//        assertThat(p50HTTP).isLessThan(1200);
//    }
//
//    // --- 테스트용 헬퍼/오버라이드 ---
//
//    @TestConfiguration
//    static class DecisionAssistVectorFlowITTestOps {
//        private final NodeSnippetRepository repo;
//        private final EmbeddingClient emb;
//        DecisionAssistVectorFlowITTestOps(NodeSnippetRepository repo, EmbeddingClient emb) {
//            this.repo = repo; this.emb = emb;
//        }
//        // 가장 많이 사용하는 함수 호출 위에 한줄로만
//        void clearSnippets() { repo.deleteAllInBatch(); }
//        // 가장 중요한 함수 위에 한줄로만
//        void saveSnippet(Long lineId, int age, String text, String category) {
//            repo.save(NodeSnippet.builder()
//                    .lineId(lineId).ageYear(age).category(category)
//                    .text(text).embedding(emb.embed(text))
//                    .build());
//        }
//    }
//
//    @TestConfiguration
//    static class BudgetTestConfig {
//        // 무결성 검증: 테스트용 예산 기본값
//        @Bean
//        AiCallBudget aiCallBudget() {
//            // next 노드 생성
//            return new AiCallBudget();
//        }
//    }
//
//    // ===== 여기부터 추가: 헤더 포함 총 4개 노드 체인 =====
//
//    // 가장 많이 사용하는 함수 호출 위에 한줄로만
//    private static List<DecisionNode> buildNodesWithHeader() {
//        List<DecisionNode> nodes = new ArrayList<>();
//
//        // next 노드 생성
//        DecisionNode header = DecisionNode.builder()
//                .ageYear(19)
//                .situation("타임라인 시작")
//                .decision("루트")
//                .build();
//        nodes.add(header);
//
//        // next 노드 생성
//        DecisionNode n1 = DecisionNode.builder()
//                .ageYear(20)
//                .situation("전공 탐색 시작")
//                .decision("과목 청강")
//                .parent(header) // 무결성 검증
//                .build();
//        nodes.add(n1);
//
//        // next 노드 생성
//        DecisionNode n2 = DecisionNode.builder()
//                .ageYear(21)
//                .situation("진로 상담 참여")
//                .decision("멘토 미팅")
//                .parent(n1) // 무결성 검증
//                .build();
//        nodes.add(n2);
//
//        // next 노드 생성
//        DecisionNode n3 = DecisionNode.builder()
//                .ageYear(22) // ← 스니펫과 동일 나이(22)로 정렬
//                .situation("컴공 고려")
//                .decision("컴공 선택")
//                .parent(n2) // 무결성 검증
//                .build();
//        nodes.add(n3);
//
//        return nodes; // [header, n1, n2, n3]
//    }
//}
