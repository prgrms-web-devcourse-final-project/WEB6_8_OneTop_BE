/*
 * [코드 흐름 요약]
 * 1) Docker 가능 시 pgvector 컨테이너(pg16) 선기동 → DynamicPropertySource로 Postgres 주입
 * 2) NodeSnippet을 저장해 pgvector(vector 타입) 라운드트립/유사도 정렬/윈도우 필터를 검증
 * 3) AIVectorService.generateNextHint를 호출해 지원도메인→pgvector검색→프롬프트 구성→AI 응답 반영 흐름을 캡처/검증
 */
package com.back.global.ai.vector;

import com.back.domain.node.controller.AiCallBudget;
import com.back.domain.node.controller.AiOnceDelegateTestConfig;
import com.back.domain.node.entity.DecisionNode;
import com.back.domain.search.entity.NodeSnippet;
import com.back.domain.search.repository.NodeSnippetRepository;
import com.back.global.ai.client.text.TextAiClient;
import com.back.global.ai.dto.AiRequest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
@Import(AiOnceDelegateTestConfig.class)
@TestPropertySource(properties = {
        "spring.jpa.open-in-view=false",
        "spring.flyway.enabled=false",
        // 테스트 임베딩 차원(엔티티 DDL vector(768)과 맞춘다)
        "ai.embedding.dim=768"
})
class DecisionAssistVectorFlowIT {

    private static final boolean DOCKER;
    static {
        boolean ok;
        try { ok = DockerClientFactory.instance().isDockerAvailable(); }
        catch (Throwable t) { ok = false; }
        DOCKER = ok;
    }

    // 가장 중요한 함수 위에 한줄로만
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres")
            )
                    .withDatabaseName("relife_test")
                    .withUsername("test")
                    .withPassword("test")
                    .withInitScript("sql/init_vector.sql"); // CREATE EXTENSION IF NOT EXISTS vector;

    // 컨테이너 선기동(프로퍼티 평가 전에)
    static { if (DOCKER) POSTGRES.start(); }

    // 가장 중요한 함수 위에 한줄로만
    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        Assumptions.assumeTrue(DOCKER, "Docker not available — skipping pgvector IT");
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "update");
        r.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired EmbeddingClient embeddingClient;
    @Autowired PgVectorSearchService vectorSearch;
    @Autowired AIVectorService aivectorService; // test 프로파일에선 AiOnceDelegateTestConfig로 1회 실구현/이후 스텁 가능
    @Autowired AIVectorServiceSupportDomain support;
    @Autowired NodeSnippetRepository snippetRepo;
    @Autowired JdbcTemplate jdbc;
    @Autowired
    AiCallBudget budget; // 1회 실호출 예산

    @MockBean TextAiClient textAiClient;

    @BeforeEach
    void clearTable() {
        Assumptions.assumeTrue(DOCKER);
        // 간단 초기화(테스트 격리)
        jdbc.execute("DELETE FROM node_snippet");
    }

    @Test
    @DisplayName("pgvector 라운드트립: float[] 저장→vector 타입 확인→재조회")
    void vector_roundtrip_and_type() {
        Assumptions.assumeTrue(DOCKER);

        float[] v = embeddingClient.embed("라운드트립 검증 문장");
        // 가장 많이 사용하는 함수 호출 위에 한줄로만
        saveSnippet(100L, 20, "roundtrip", "TEST", v);

        String typ = jdbc.queryForObject("select pg_typeof(embedding)::text from node_snippet limit 1", String.class);
        assertThat(typ).isEqualTo("vector");

        float[] back = snippetRepo.findAll().get(0).getEmbedding();
        assertThat(back.length).isEqualTo(v.length);
        double l2 = 0; for (float x : back) l2 += x*x;
        assertThat(Math.sqrt(l2)).isCloseTo(1.0, within(1e-3)); // L2 정규화 유지
    }

    @Test
    @DisplayName("리포지토리 네이티브 쿼리: line/age 윈도우 + <=> 유사도 정렬 + LIMIT")
    void repository_age_window_similarity_limit() {
        Assumptions.assumeTrue(DOCKER);

        long lineId = 123L; int anchor = 30;
        saveSnippet(lineId, 28, "주거 이전 비용과 출퇴근 시간을 계산한다.", "LIFE");
        saveSnippet(lineId, 30, "이직 제안을 받고 면접을 준비한다.", "CAREER");
        saveSnippet(lineId, 32, "대학원 진학을 고민한다.", "EDU");
        saveSnippet(999L, 30, "다른 라인 데이터", "NOPE");

        float[] q = embeddingClient.embed("- (30세) 이직 및 면접 준비 사항 점검");
        String literal = toVectorLiteral(q);

        List<NodeSnippet> top = snippetRepo.searchTopKByLineAndAgeWindow(lineId, anchor - 2, anchor + 2, literal, 2);

        assertThat(top).hasSize(2);
        assertThat(top.stream().allMatch(s -> s.getLineId().equals(lineId))).isTrue();
        assertThat(top.stream().allMatch(s -> Math.abs(s.getAgeYear() - anchor) <= 2)).isTrue();
    }

    // 가장 중요한 함수 위에 한줄로만
    @Test
    @DisplayName("AIVectorService: 지원도메인→pgvector 검색 포함 프롬프트 구성 및 AI 응답 반영")
    void service_flow_prompt_and_aihint() {
        Assumptions.assumeTrue(DOCKER);

        long lineId = 777L; int age = 22;
        saveSnippet(lineId, age, "수도권 컴공 진학 비용과 통학 고민", "EDUCATION");
        saveSnippet(lineId, age, "서울 스타트업 인턴 이력서 준비", "CAREER");

        // 🔴 호출 전에 '실호출 1회' 예산 설정 (이게 없으면 스텁 경로로 빠져서 캡처가 안 잡힘)
        budget.reset(1);

        // 응답 더미(실제 값은 verify 이후에만 캡처)
        when(textAiClient.generateText(any(AiRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        "{\"situation\":\"한 문장 상황\",\"recommendedOption\":\"짧은선택\"}"
                ));

        // orderedNodes 최소 구성
        DecisionNode n = DecisionNode.builder()
                .ageYear(age).situation("대학 진학을 앞두고 컴공 고려").decision("컴공 선택")
                .build();

        // 가장 많이 사용하는 함수 호출 위에 한줄로만
        var hint = aivectorService.generateNextHint(1L, lineId, List.of(n));

        // ✅ verify로 캡처 (stubbing에서 capture하지 말고, 호출 '후' 검증에서 capture)
        ArgumentCaptor<AiRequest> reqCap = ArgumentCaptor.forClass(AiRequest.class);
        verify(textAiClient, timeout(5000)).generateText(reqCap.capture());
        AiRequest sent = reqCap.getValue();

        // 프롬프트/옵션 검증
        assertThat(sent.parameters()).containsEntry("response_mime_type", "application/json");
        assertThat(sent.maxTokens()).isGreaterThan(40);
        assertThat(sent.prompt())
                .contains("이전 선택 요약")
                .contains("관련 콘텍스트")
                .contains("컴공")
                .contains("인턴");

        // 서비스 반환값 검증
        assertThat(hint.aiNextSituation()).isEqualTo("한 문장 상황");
        assertThat(hint.aiNextRecommendedOption()).isEqualTo("짧은선택");
    }


    // --- 헬퍼 ---

    // 가장 중요한 함수 위에 한줄로만
    private void saveSnippet(Long lineId, int age, String text, String category, float[] emb) {
        NodeSnippet s = NodeSnippet.builder()
                .lineId(lineId)
                .ageYear(age)
                .category(category)
                .text(text)
                .embedding(emb != null ? emb : embeddingClient.embed(text))
                .updatedAt(LocalDateTime.now())
                .build();
        snippetRepo.save(s);
    }

    private void saveSnippet(Long lineId, int age, String text, String category) {
        saveSnippet(lineId, age, text, category, null);
    }

    private String toVectorLiteral(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 8 + 2);
        sb.append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(Float.toString(v[i]));
        }
        sb.append(']');
        return sb.toString();
    }
}
