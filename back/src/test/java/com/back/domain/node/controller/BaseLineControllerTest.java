/**
 * [TEST SUITE] Re:Life — BaseLine/BaseNode 통합 테스트 (보안 적용 전체 리팩터링 버전)
 *
 * 목적/흐름 요약
 * - 모든 요청 경로에 대해 "보안 필터 활성화(addFilters=true)" 전제를 명시하고,
 *   인증 필요 엔드포인트에는 반드시 인증(.with(user(...)))을, 상태 변경 POST에는 CSRF(.with(csrf()))를 부착한다.
 * - 라인 저장 → 노드/피벗/트리 조회 → 예외/롤백 → 내 라인 목록(/mine)까지 전 구간을 일관된 인증 전략으로 검증한다.
 * - 응답 스키마 변경/확장 가능성에 대비해 JSON 경로 접근을 명확히(body.get("baseNodes") 등) 고정한다.
 */
package com.back.domain.node.controller;

import com.back.domain.node.entity.NodeCategory;
import com.back.domain.node.repository.BaseLineRepository;
import com.back.domain.node.repository.BaseNodeRepository;
import com.back.domain.user.entity.*;
import com.back.domain.user.repository.UserRepository;
import com.back.global.security.CustomUserDetails;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Re:Life — BaseLine/BaseNode 통합 테스트 (보안 일괄 적용)")
@SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED)
@Sql(
        statements = {
                "SET REFERENTIAL_INTEGRITY FALSE",

                "TRUNCATE TABLE BASELINE_PATCHES",
                "TRUNCATE TABLE BASELINE_COMMITS",
                "TRUNCATE TABLE BASELINE_BRANCHES",
                "TRUNCATE TABLE NODE_ATOM_VERSIONS",
                "TRUNCATE TABLE NODE_ATOMS",
                "TRUNCATE TABLE DECISION_NODES",
                "TRUNCATE TABLE DECISION_LINES",
                "TRUNCATE TABLE BASE_NODES",
                "TRUNCATE TABLE BASE_LINES",
                "TRUNCATE TABLE USERS",

                "ALTER TABLE BASELINE_PATCHES ALTER COLUMN ID RESTART WITH 1",
                "ALTER TABLE BASELINE_COMMITS ALTER COLUMN ID RESTART WITH 1",
                "ALTER TABLE BASELINE_BRANCHES ALTER COLUMN ID RESTART WITH 1",
                "ALTER TABLE NODE_ATOM_VERSIONS ALTER COLUMN ID RESTART WITH 1",
                "ALTER TABLE NODE_ATOMS ALTER COLUMN ID RESTART WITH 1",
                "ALTER TABLE DECISION_NODES ALTER COLUMN ID RESTART WITH 1",
                "ALTER TABLE DECISION_LINES ALTER COLUMN ID RESTART WITH 1",
                "ALTER TABLE BASE_NODES ALTER COLUMN ID RESTART WITH 1",
                "ALTER TABLE BASE_LINES ALTER COLUMN ID RESTART WITH 1",
                "ALTER TABLE USERS ALTER COLUMN ID RESTART WITH 1",
                "SET REFERENTIAL_INTEGRITY TRUE"
        },
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD
)
public class BaseLineControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper om;
    @Autowired private UserRepository userRepository;

    @Autowired private BaseLineRepository baseLineRepository;
    @Autowired private BaseNodeRepository baseNodeRepository;

    private Long userId;

    @BeforeEach
    void initUser() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        User user = User.builder()
                .email("user_" + uid + "@test.local")
                .role(Role.USER)
                .birthdayAt(LocalDateTime.now().minusYears(25))
                .gender(Gender.M)
                .mbti(Mbti.INTJ)
                .beliefs("NONE")
                .authProvider(AuthProvider.LOCAL)
                .nickname("tester-" + uid)
                .username("name-" + uid)
                .build();
        userId = userRepository.save(user).getId();
    }
    // 가장 중요한 함수 한줄 요약: 라인을 저장하고 baseLineId를 반환(인증/CSRF)
    private Long saveAndGetBaseLineId() throws Exception {
        var res = mockMvc.perform(post("/api/v1/base-lines/bulk")
                        .with(authed(userId))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sampleLineJson(userId)))
                .andExpect(status().isCreated())
                .andReturn();
        return om.readTree(res.getResponse().getContentAsString()).get("baseLineId").asLong();
    }

    // 가장 중요한 함수 한줄 요약: SecurityContextHolder에 인증 토큰 세팅
    private void setAuth(CustomUserDetails cud) {
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(new UsernamePasswordAuthenticationToken(cud, null, cud.getAuthorities()));
        SecurityContextHolder.setContext(ctx);
    }

    // 가장 많이 사용하는 함수 호출 한줄 요약: uid로 인증 RequestPostProcessor 생성
    private RequestPostProcessor authed(Long uid) {
        var me = userRepository.findById(uid).orElseThrow();
        return user(new CustomUserDetails(me));
    }

    // (자주 쓰는) 정상 입력 샘플 JSON 생성
    private String sampleLineJson(Long uid) {
        return """
        { "userId": %d,
          "nodes": [
            {"category":"%s","situation":"고등학교 졸업","decision":"고등학교 졸업","ageYear":18},
            {"category":"%s","situation":"대학 입학","decision":"대학 입학","ageYear":20},
            {"category":"%s","situation":"첫 인턴","decision":"첫 인턴","ageYear":22},
            {"category":"%s","situation":"결말","decision":"결말","ageYear":24}
          ]
        }
        """.formatted(uid,
                NodeCategory.EDUCATION, NodeCategory.CAREER, NodeCategory.CAREER, NodeCategory.ETC);
    }

    // ===========================
    // 베이스 라인 생성/검증
    // ===========================
    @Nested
    @DisplayName("베이스 라인 생성/검증")
    class BaseLine_Create_Validate {

        @Test
        @DisplayName("성공 : 헤더~꼬리 4개 노드를 /bulk로 저장하면 201과 생성 id 목록을 반환(인증/CSRF)")
        void success_bulkCreateLine() throws Exception {
            String payload = sampleLineJson(userId);
            mockMvc.perform(post("/api/v1/base-lines/bulk")
                            .with(authed(userId)) // 인증
                            .with(csrf())         // CSRF
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.baseLineId").exists())
                    .andExpect(jsonPath("$.nodes.length()").value(4));
        }

        @Test
        @DisplayName("실패 : nodes가 비어있으면 400/C001 (인증/CSRF)")
        void fail_nodesEmpty() throws Exception {
            String bad = """
                { "userId": %d, "nodes": [] }
                """.formatted(userId);

            mockMvc.perform(post("/api/v1/base-lines/bulk")
                            .with(authed(userId))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bad))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("C001"));
        }

        @Test
        @DisplayName("성공 : nodes 길이 1이어도 헤더/테일 자동 부착(총 3개) (인증/CSRF + 조회시 인증)")
        void success_nodesSinglePivot_autocompleteEnds() throws Exception {
            String one = """
                { "userId": %d, "nodes": [ { "category":"%s", "situation":"단건 피벗", "decision":"단건 피벗", "ageYear":18 } ] }
                """.formatted(userId, NodeCategory.EDUCATION);

            var res = mockMvc.perform(post("/api/v1/base-lines/bulk")
                            .with(authed(userId))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(one))
                    .andExpect(status().isCreated())
                    .andReturn();

            long baseLineId = om.readTree(res.getResponse().getContentAsString()).get("baseLineId").asLong();

            mockMvc.perform(get("/api/v1/base-lines/{id}/nodes", baseLineId)
                            .with(authed(userId)))
                    .andExpect(status().isOk());

            var nodesRes = mockMvc.perform(get("/api/v1/base-lines/{id}/nodes", baseLineId)
                            .with(authed(userId)))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode nodes = om.readTree(nodesRes.getResponse().getContentAsString());
            assertThat(nodes).hasSize(3);
            assertThat(nodes.get(0).get("ageYear").asInt()).isEqualTo(18);
            assertThat(nodes.get(1).get("ageYear").asInt()).isEqualTo(18);
            assertThat(nodes.get(2).get("ageYear").asInt()).isEqualTo(18);

            var pivotsRes = mockMvc.perform(get("/api/v1/base-lines/{id}/pivots", baseLineId)
                            .with(authed(userId)))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode pivots = om.readTree(pivotsRes.getResponse().getContentAsString()).get("pivots");
            assertThat(pivots).hasSize(1);
            assertThat(pivots.get(0).get("ageYear").asInt()).isEqualTo(18);
        }

        @Test
        @DisplayName("실패 : 음수 ageYear 포함 시 400/C001 (인증/CSRF)")
        void fail_negativeAge() throws Exception {
            String bad = """
            { "userId": %d,
              "nodes": [
                {"category":"%s","situation":"음수나이","decision":"음수나이","ageYear":-1},
                {"category":"%s","situation":"꼬리","decision":"꼬리","ageYear":24}
              ]
            }
            """.formatted(userId, NodeCategory.EDUCATION, NodeCategory.ETC);

            mockMvc.perform(post("/api/v1/base-lines/bulk")
                            .with(authed(userId))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bad))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("C001"));
        }

        @Test
        @DisplayName("실패 : situation 길이 초과 시 400/C001 (인증/CSRF)")
        void fail_longText() throws Exception {
            String longText = "가".repeat(5000);
            String bad = """
            { "userId": %d,
              "nodes": [
                {"category":"%s","situation":"%s","decision":"%s","ageYear":18},
                {"category":"%s","situation":"정상","decision":"정상","ageYear":24}
              ]
            }
            """.formatted(userId, NodeCategory.EDUCATION, longText, longText, NodeCategory.ETC);

            mockMvc.perform(post("/api/v1/base-lines/bulk")
                            .with(authed(userId))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bad))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("C001"));
        }

        @Test
        @DisplayName("실패 : 중간 invalid 발생 시 트랜잭션 롤백(인증/CSRF)")
        void fail_midInvalid_rollback() throws Exception {
            long beforeLines = baseLineRepository.count();
            long beforeNodes = baseNodeRepository.count();

            String midBad = """
            { "userId": %d,
              "nodes": [
                {"category":"%s","situation":"헤더","decision":"헤더","ageYear":18},
                {"category":"%s","situation":"중간-invalid","decision":"중간-invalid","ageYear":-1},
                {"category":"%s","situation":"꼬리","decision":"꼬리","ageYear":24}
              ]
            }
            """.formatted(userId, NodeCategory.EDUCATION, NodeCategory.CAREER, NodeCategory.ETC);

            mockMvc.perform(post("/api/v1/base-lines/bulk")
                            .with(authed(userId))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(midBad))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("C001"));

            assertThat(baseLineRepository.count()).isEqualTo(beforeLines);
            assertThat(baseNodeRepository.count()).isEqualTo(beforeNodes);
        }
    }

    // ===========================
    // 베이스 라인 조회
    // ===========================
    @Nested
    @DisplayName("베이스 라인 조회")
    class BaseLine_Read {

        @Test
        @DisplayName("성공 : 저장된 라인을 ageYear ASC(id ASC tie-break)으로 조회 (인증)")
        void success_readLine_sortedByAge() throws Exception {
            Long baseLineId = saveAndGetBaseLineId();

            var res = mockMvc.perform(get("/api/v1/base-lines/{id}/nodes", baseLineId)
                            .with(authed(userId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(4))
                    .andReturn();

            JsonNode arr = om.readTree(res.getResponse().getContentAsString());
            assertThat(arr.get(0).get("ageYear").asInt()).isEqualTo(18);
            assertThat(arr.get(3).get("ageYear").asInt()).isEqualTo(24);
        }

        @Test
        @DisplayName("실패 : 존재하지 않는 baseLineId 조회 시 404/N002 (인증)")
        void fail_lineNotFound() throws Exception {
            long unknownId = 9_999_999L;
            mockMvc.perform(get("/api/v1/base-lines/{id}/nodes", unknownId)
                            .with(authed(userId)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("N002"))
                    .andExpect(jsonPath("$.message").exists());
        }


    }

    // ===========================
    // 베이스 노드 조회
    // ===========================
    @Nested
    @DisplayName("베이스 노드 조회")
    class BaseNode_Read {

        @Test
        @DisplayName("성공 : /nodes/{baseNodeId} 단건 조회 (인증)")
        void success_readSingleNode() throws Exception {
            Long baseLineId = createLineAndGetId(userId);

            var listRes = mockMvc.perform(get("/api/v1/base-lines/{id}/nodes", baseLineId)
                            .with(authed(userId)))
                    .andExpect(status().isOk())
                    .andReturn();
            var arr = om.readTree(listRes.getResponse().getContentAsString());
            long nodeId = arr.get(0).get("id").asLong();

            mockMvc.perform(get("/api/v1/base-lines/nodes/{nodeId}", nodeId)
                            .with(authed(userId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(nodeId))
                    .andExpect(jsonPath("$.baseLineId").value(baseLineId));
        }

        @Test
        @DisplayName("실패 : 존재하지 않는 baseNodeId 단건 조회 시 404/N001 (인증)")
        void fail_nodeNotFound() throws Exception {
            long unknownNode = 9_999_999L;
            mockMvc.perform(get("/api/v1/base-lines/nodes/{nodeId}", unknownNode)
                            .with(authed(userId)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("N001"))
                    .andExpect(jsonPath("$.message").exists());
        }

        // 가장 많이 사용하는 함수 호출 한줄 요약: 뒤섞인 입력으로 라인 생성 후 baseLineId 반환(인증/CSRF)
        private Long createLineAndGetId(Long uid) throws Exception {
            var res = mockMvc.perform(post("/api/v1/base-lines/bulk")
                            .with(authed(uid))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(sampleShuffledJson(uid)))
                    .andExpect(status().isCreated())
                    .andReturn();
            return om.readTree(res.getResponse().getContentAsString()).get("baseLineId").asLong();
        }

        // (자주 쓰는) 정렬 안정성 검증용 뒤섞인 입력 샘플 생성
        private String sampleShuffledJson(Long uid) {
            return """
            { "userId": %d,
              "nodes": [
                {"category":"%s","situation":"첫 인턴","decision":"첫 인턴","ageYear":22},
                {"category":"%s","situation":"결말","decision":"결말","ageYear":24},
                {"category":"%s","situation":"고등학교 졸업","decision":"고등학교 졸업","ageYear":18},
                {"category":"%s","situation":"대학 입학","decision":"대학 입학","ageYear":20}
              ]
            }
            """.formatted(uid,
                    NodeCategory.CAREER, NodeCategory.ETC, NodeCategory.EDUCATION, NodeCategory.CAREER);
        }
    }

    // ===========================
    // 피벗 규칙 검증
    // ===========================
    @Nested
    @DisplayName("피벗 규칙 검증")
    class Pivot_Rules {

        @Test
        @DisplayName("성공 : 피벗은 헤더/꼬리 제외·중복 제거·오름차순 정렬 보장 (인증)")
        void success_pivotRules() throws Exception {
            String withDup = """
            { "userId": %d,
              "nodes": [
                {"category":"%s","situation":"중간1","decision":"중간1","ageYear":18},
                {"category":"%s","situation":"중간2","decision":"중간2","ageYear":20},
                {"category":"%s","situation":"중복20","decision":"중복20","ageYear":20},
                {"category":"%s","situation":"중간3","decision":"중간3","ageYear":22},
                {"category":"%s","situation":"중간4","decision":"중간4","ageYear":24}
              ]
            }
            """.formatted(userId,
                    NodeCategory.EDUCATION, NodeCategory.CAREER, NodeCategory.CAREER, NodeCategory.CAREER, NodeCategory.ETC);

            var res = mockMvc.perform(post("/api/v1/base-lines/bulk")
                            .with(authed(userId))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(withDup))
                    .andExpect(status().isCreated())
                    .andReturn();

            long baseLineId = om.readTree(res.getResponse().getContentAsString()).get("baseLineId").asLong();

            var pivotsRes = mockMvc.perform(get("/api/v1/base-lines/{id}/pivots", baseLineId)
                            .with(authed(userId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pivots.length()").value(4))
                    .andReturn();

            var pivots = om.readTree(pivotsRes.getResponse().getContentAsString()).get("pivots");
            assertThat(pivots.get(0).get("ageYear").asInt()).isEqualTo(18);
            assertThat(pivots.get(1).get("ageYear").asInt()).isEqualTo(20);
            assertThat(pivots.get(2).get("ageYear").asInt()).isEqualTo(22);
            assertThat(pivots.get(3).get("ageYear").asInt()).isEqualTo(24);
        }
    }

    // ===========================
    // 트리 조회 (라인 단위)
    // ===========================
    @Nested
    @DisplayName("트리 조회(라인 단위)")
    class Tree_Read_For_BaseLine {

        @Test
        @DisplayName("성공 : 결정 라인이 없으면 baseNodes만, decisionNodes는 빈 배열 (인증)")
        void success_tree_noDecision() throws Exception {
            Long baseLineId = saveAndGetBaseLineId();

            var res = mockMvc.perform(get("/api/v1/base-lines/{id}/tree", baseLineId)
                            .with(authed(userId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.baseNodes.length()").value(4))
                    .andExpect(jsonPath("$.decisionNodes.length()").value(0))
                    .andReturn();

            JsonNode body = om.readTree(res.getResponse().getContentAsString());
            assertThat(body.get("baseNodes").get(0).get("ageYear").asInt()).isEqualTo(18);
            assertThat(body.get("baseNodes").get(3).get("ageYear").asInt()).isEqualTo(24);
        }

        @Test
        @DisplayName("성공 : from-base + next 추가 후 결정 노드도 함께 정렬되어 반환 (인증/CSRF)")
        void success_tree_withDecisions_secured() throws Exception {
            // given
            // 1) base bulk (POST + csrf + 인증)
            var created = mockMvc.perform(post("/api/v1/base-lines/bulk")
                            .with(authed(userId))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(sampleLineJson(userId)))
                    .andExpect(status().isCreated())
                    .andReturn();
            long baseLineId = om.readTree(created.getResponse().getContentAsString())
                    .get("baseLineId").asLong();

            // 2) pivots (GET + 인증)
            var pivotsRes = mockMvc.perform(get("/api/v1/base-lines/{id}/pivots", baseLineId)
                            .with(authed(userId)))
                    .andExpect(status().isOk())
                    .andReturn();
            int pivotAge = om.readTree(pivotsRes.getResponse().getContentAsString())
                    .get("pivots").get(0).get("ageYear").asInt();

            // 3) from-base (POST + csrf + 인증)
            String fromBasePayload = """
            {
              "userId": %d,
              "baseLineId": %d,
              "pivotAge": %d,
              "selectedAltIndex": 0,
              "category": "%s",
              "situation": "분기 시작",
              "options": ["선택-A"],
              "selectedIndex": 0
            }
            """.formatted(userId, baseLineId, pivotAge, NodeCategory.CAREER);

            var fb = mockMvc.perform(post("/api/v1/decision-flow/from-base")
                            .with(authed(userId))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fromBasePayload))
                    .andExpect(status().isCreated())
                    .andReturn();

            JsonNode fbBody = om.readTree(fb.getResponse().getContentAsString());
            long headDecisionNodeId = fbBody.get("id").asLong();

            // 4) next (POST + csrf + 인증)
            String nextPayload = """
            {
              "userId": %d,
              "parentDecisionNodeId": %d,
              "category": "%s",
              "situation": "다음 선택",
              "options": ["선택-A-후속"],
              "selectedIndex": 0,
              "ageYear": 22
            }
            """.formatted(userId, headDecisionNodeId, NodeCategory.CAREER);

            mockMvc.perform(post("/api/v1/decision-flow/next")
                            .with(authed(userId))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(nextPayload))
                    .andExpect(status().isCreated());

            // 5) tree (GET + 인증)
            var res = mockMvc.perform(get("/api/v1/base-lines/{id}/tree", baseLineId)
                            .with(authed(userId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.baseNodes.length()").value(4))
                    .andExpect(jsonPath("$.decisionNodes.length()").value(3))
                    .andReturn();

            JsonNode tree = om.readTree(res.getResponse().getContentAsString());
            assertThat(tree.get("decisionNodes").get(1).get("ageYear").asInt()).isEqualTo(pivotAge);
            assertThat(tree.get("decisionNodes").get(2).get("ageYear").asInt()).isEqualTo(22);
        }

        @Test
        @DisplayName("실패 : 존재하지 않는 baseLineId로 트리 조회 시 404/N002 (인증)")
        void fail_tree_lineNotFound() throws Exception {
            long unknownId = 9_999_999L;
            mockMvc.perform(get("/api/v1/base-lines/{id}/tree", unknownId)
                            .with(authed(userId)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("N002"))
                    .andExpect(jsonPath("$.message").exists());
        }
    }

    @Nested
    @DisplayName("내 베이스라인 목록(/mine)")
    class BaseLine_Mine {

        @AfterEach
        void clearCtx() { SecurityContextHolder.clearContext(); }

        @Test
        @DisplayName("성공 : /mine — 최소 1개 라인 생성 후 id/title 포함 목록 반환 (인증)")
        void success_mine_returnsList() throws Exception {
            // given: 라인 1개 생성 (인증/CSRF)
            mockMvc.perform(post("/api/v1/base-lines/bulk")
                            .with(authed(userId))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(sampleLineJson(userId)))
                    .andExpect(status().isCreated());

            var me = userRepository.findById(userId).orElseThrow();
            setAuth(new CustomUserDetails(me)); // 컨텍스트 세팅(보수적)

            mockMvc.perform(get("/api/v1/base-lines/mine")
                            .with(authed(userId))) // 요청에도 인증 주입
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").exists())
                    .andExpect(jsonPath("$[0].title").exists());
        }

        @Test
        @DisplayName("성공 : /mine — 라인이 없는 사용자에겐 빈 배열([]) 반환 (인증)")
        void success_mine_emptyForUserWithoutLines() throws Exception {
            String uid = UUID.randomUUID().toString().substring(0, 8);
            User emptyUser = User.builder()
                    .email("nouser_" + uid + "@test.local")
                    .role(Role.USER)
                    .birthdayAt(LocalDateTime.now().minusYears(20))
                    .gender(Gender.F)
                    .mbti(Mbti.INFP)
                    .beliefs("NONE")
                    .authProvider(AuthProvider.LOCAL)
                    .nickname("nouser-" + uid)
                    .username("nouser-" + uid)
                    .build();
            userRepository.save(emptyUser);

            setAuth(new CustomUserDetails(emptyUser));

            mockMvc.perform(get("/api/v1/base-lines/mine")
                            .with(user(new CustomUserDetails(emptyUser))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }
}
