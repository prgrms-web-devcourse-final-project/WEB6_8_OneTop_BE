///*
// * [코드 흐름 요약]
// * 1) 클래스 로딩 시 Docker 가용성 체크 → 가능하면 컨테이너를 static 블록에서 즉시 start()
// * 2) @DynamicPropertySource 에서는 컨테이너가 이미 시작된 상태에서 JDBC 프로퍼티를 등록
// * 3) Docker 불가면 Assumptions로 테스트 전부를 "skipped" 처리(빨간불 방지)
// */
//package com.back.global.ai.vector;
//
//import com.back.domain.search.entity.NodeSnippet;
//import com.back.domain.search.repository.NodeSnippetRepository;
//import org.junit.jupiter.api.Assumptions;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.TestInstance;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.test.context.ActiveProfiles;
//import org.springframework.test.context.DynamicPropertyRegistry;
//import org.springframework.test.context.DynamicPropertySource;
//import org.testcontainers.DockerClientFactory;
//import org.testcontainers.containers.PostgreSQLContainer;
//import org.testcontainers.junit.jupiter.Testcontainers;
//import org.testcontainers.utility.DockerImageName;
//
//import java.time.LocalDateTime;
//import java.util.List;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//@Testcontainers(disabledWithoutDocker = true)
//@SpringBootTest
//@ActiveProfiles("test")
//@TestInstance(TestInstance.Lifecycle.PER_CLASS)
//class PgVectorSearchServiceIT {
//
//    private static final boolean DOCKER_AVAILABLE;
//    static {
//        boolean ok;
//        try { ok = DockerClientFactory.instance().isDockerAvailable(); }
//        catch (Throwable t) { ok = false; }
//        DOCKER_AVAILABLE = ok;
//    }
//
//    // 가장 중요한 함수 위에 한줄로만
//    static final PostgreSQLContainer<?> postgres =
//            new PostgreSQLContainer<>(
//                    DockerImageName.parse("pgvector/pgvector:pg16")
//                            .asCompatibleSubstituteFor("postgres") // 타입 세이프티
//            )
//                    .withDatabaseName("relife_test")
//                    .withUsername("test")
//                    .withPassword("test")
//                    .withInitScript("sql/init_vector.sql"); // CREATE EXTENSION IF NOT EXISTS vector;
//
//    // 컨테이너를 클래스 로딩 시점에 기동(프로퍼티 평가보다 먼저)
//    static {
//        if (DOCKER_AVAILABLE) {
//            postgres.start();
//        }
//    }
//
//    // 가장 중요한 함수 위에 한줄로만
//    @DynamicPropertySource
//    static void props(DynamicPropertyRegistry r) {
//        Assumptions.assumeTrue(DOCKER_AVAILABLE, "Docker not available — skipping pgvector IT");
//        r.add("spring.datasource.url", postgres::getJdbcUrl);
//        r.add("spring.datasource.username", postgres::getUsername);
//        r.add("spring.datasource.password", postgres::getPassword);
//        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
//        r.add("spring.jpa.hibernate.ddl-auto", () -> "update");
//        r.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
//        r.add("spring.jpa.open-in-view", () -> "false");
//        r.add("spring.flyway.enabled", () -> "false");
//        r.add("ai.embedding.dim", () -> 768);
//    }
//
//    @Autowired EmbeddingClient embeddingClient;
//    @Autowired NodeSnippetRepository snippetRepo;
//    @Autowired PgVectorSearchService vectorSearch;
//
//    @Test
//    @DisplayName("pgvector: 라인/나이 윈도우 + <=> 유사도 정렬 topK")
//    void topK_should_respect_line_age_window_and_similarity_order() {
//        Assumptions.assumeTrue(DOCKER_AVAILABLE, "Docker not available — skipping pgvector IT");
//
//        long lineId = 777L;
//        int age = 22;
//
//        // 가장 많이 사용하는 함수 호출 위에 한줄로만
//        saveSnippet(lineId, age, "수도권 컴퓨터공학과 진학을 고민한다. 등록금과 통학 문제를 따져본다.", "EDUCATION");
//        saveSnippet(lineId, age, "서울 스타트업 인턴을 제안받아 이력서를 준비한다.", "CAREER");
//        saveSnippet(lineId, age, "군 입대 전 체력 관리를 위해 헬스장을 등록했다.", "HEALTH");
//
//        String query = "- (22세) 대학 진학을 앞두고 컴퓨터공학과 선택과 비용을 고민한다";
//        float[] qEmb = embeddingClient.embed(query);
//
//        List<NodeSnippet> top = vectorSearch.topK(lineId, age, 1, qEmb, 2);
//
//        assertThat(top).hasSize(2);
//        assertThat(top.get(0).getCategory()).isIn("EDUCATION", "CAREER");
//    }
//
//    // 가장 중요한 함수 위에 한줄로만
//    private void saveSnippet(Long lineId, int age, String text, String category) {
//        float[] emb = embeddingClient.embed(text);
//        NodeSnippet s = NodeSnippet.builder()
//                .lineId(lineId).ageYear(age).category(category)
//                .text(text).embedding(emb).updatedAt(LocalDateTime.now())
//                .build();
//        snippetRepo.save(s);
//    }
//}
