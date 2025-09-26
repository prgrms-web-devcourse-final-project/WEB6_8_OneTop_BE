/**
 * [TEST SUITE] Re:Life — BaseLine/BaseNode 통합 테스트 (분류별 정리)
 *
 * 목적
 * - 베이스 라인/노드 저장·조회, 피벗 규칙, 검증 에러, 존재하지 않는 자원 404, 트랜잭션 롤백, 정렬 안정성 통합 검증
 *
 */
package com.back.domain.node.controller;

import com.back.domain.node.dto.BaseLineBulkCreateResponse;
import com.back.domain.node.entity.NodeCategory;
import com.back.domain.node.repository.BaseLineRepository;
import com.back.domain.node.repository.BaseNodeRepository;
import com.back.domain.user.entity.*;
import com.back.domain.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Re:Life — BaseLine/BaseNode 통합 테스트")
public class BaseLineControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper om;
    @Autowired private UserRepository userRepository;

    @Autowired private BaseLineRepository baseLineRepository;
    @Autowired private BaseNodeRepository baseNodeRepository;

    private Long userId;

    @BeforeAll
    void initUser() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        User user = User.builder()
                .email("user_" + uid + "@test.local")
                .role(Role.GUEST)
                .birthdayAt(LocalDateTime.now().minusYears(25))
                .gender(Gender.M)
                .mbti(Mbti.INTJ)
                .beliefs("NONE")
                .authProvider(AuthProvider.GUEST)
                .nickname("tester-" + uid)
                .username("name-" + uid)
                .build();
        userId = userRepository.save(user).getId();
    }

    // ===========================
    // 베이스 라인 생성/검증
    // ===========================
    @Nested
    @DisplayName("베이스 라인 생성/검증")
    class BaseLine_Create_Validate {

        @Test
        @DisplayName("성공 : 헤더~꼬리 4개 노드 라인을 /bulk로 저장하면 201과 생성 id 목록을 반환한다")
        void success_bulkCreateLine() throws Exception {
            String payload = sampleLineJson(userId);

            var res = mockMvc.perform(post("/api/v1/base-lines/bulk")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.baseLineId").exists())
                    .andExpect(jsonPath("$.nodes.length()").value(4))
                    .andReturn();

            BaseLineBulkCreateResponse body =
                    om.readValue(res.getResponse().getContentAsString(), BaseLineBulkCreateResponse.class);
            assertThat(body.baseLineId()).isNotNull();
            assertThat(body.nodes()).hasSize(4);
        }

        @Test
        @DisplayName("실패 : nodes 길이가 2 미만이면 400/C001을 반환한다")
        void fail_nodesTooShort() throws Exception {
            // decision 필수 → 단건 샘플에도 decision 채움
            String bad = """
            { "userId": %d, "nodes": [ { "category":"%s", "situation":"단건", "decision":"단건", "ageYear":18 } ] }
            """.formatted(userId, NodeCategory.EDUCATION);

            mockMvc.perform(post("/api/v1/base-lines/bulk")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bad))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("C001"));
        }

        @Test
        @DisplayName("실패 : 음수 ageYear가 포함되면 400/C001을 반환한다")
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
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bad))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("C001"));
        }

        @Test
        @DisplayName("실패 : situation 문자열 길이 초과 시 400/C001을 반환한다")
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
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bad))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("C001"));
        }

        @Test
        @DisplayName("실패 : 중간 노드 invalid 시 트랜잭션 롤백되어 어떤 엔티티도 남지 않는다")
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
        @DisplayName("성공 : 저장된 라인을 ageYear 오름차순(동률 id ASC)으로 조회할 수 있다")
        void success_readLine_sortedByAge() throws Exception {
            Long baseLineId = saveAndGetBaseLineId();

            var res = mockMvc.perform(get("/api/v1/base-lines/{id}/nodes", baseLineId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(4))
                    .andReturn();

            JsonNode arr = om.readTree(res.getResponse().getContentAsString());
            assertThat(arr.get(0).get("ageYear").asInt()).isEqualTo(18);
            assertThat(arr.get(3).get("ageYear").asInt()).isEqualTo(24);
        }

        @Test
        @DisplayName("실패 : 존재하지 않는 baseLineId로 조회 시 404/N002를 반환한다")
        void fail_lineNotFound() throws Exception {
            long unknownId = 9_999_999L;
            mockMvc.perform(get("/api/v1/base-lines/{id}/nodes", unknownId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("N002"))
                    .andExpect(jsonPath("$.message").exists());
        }

        // (가장 중요한) 라인 저장 후 baseLineId 반환
        private Long saveAndGetBaseLineId() throws Exception {
            var res = mockMvc.perform(post("/api/v1/base-lines/bulk")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(sampleLineJson(userId)))
                    .andExpect(status().isCreated())
                    .andReturn();
            return om.readTree(res.getResponse().getContentAsString()).get("baseLineId").asLong();
        }
    }

    // ===========================
    // 베이스 노드 조회
    // ===========================
    @Nested
    @DisplayName("베이스 노드 조회")
    class BaseNode_Read {

        @Test
        @DisplayName("성공 : /nodes/{baseNodeId} 단건 조회가 정상 동작한다")
        void success_readSingleNode() throws Exception {
            Long baseLineId = createLineAndGetId(userId);

            var listRes = mockMvc.perform(get("/api/v1/base-lines/{id}/nodes", baseLineId))
                    .andExpect(status().isOk())
                    .andReturn();
            var arr = om.readTree(listRes.getResponse().getContentAsString());
            long nodeId = arr.get(0).get("id").asLong();

            mockMvc.perform(get("/api/v1/base-lines/nodes/{nodeId}", nodeId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(nodeId))
                    .andExpect(jsonPath("$.baseLineId").value(baseLineId));
        }

        @Test
        @DisplayName("실패 : 존재하지 않는 baseNodeId 단건 조회 시 404/N001을 반환한다")
        void fail_nodeNotFound() throws Exception {
            long unknownNode = 9_999_999L;
            mockMvc.perform(get("/api/v1/base-lines/nodes/{nodeId}", unknownNode))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("N001"))
                    .andExpect(jsonPath("$.message").exists());
        }

        // (자주 쓰는) 뒤섞인 입력으로 라인 생성 후 baseLineId 반환(정렬 안정성 검증 겸용)
        private Long createLineAndGetId(Long uid) throws Exception {
            var res = mockMvc.perform(post("/api/v1/base-lines/bulk")
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
        @DisplayName("성공 : 피벗은 헤더/꼬리 제외, 중복 제거, 오름차순 정렬이 보장된다")
        void success_pivotRules() throws Exception {
            String withDup = """
            { "userId": %d,
              "nodes": [
                {"category":"%s","situation":"헤더","decision":"헤더","ageYear":18},
                {"category":"%s","situation":"중간1","decision":"중간1","ageYear":20},
                {"category":"%s","situation":"중복20","decision":"중복20","ageYear":20},
                {"category":"%s","situation":"중간2","decision":"중간2","ageYear":22},
                {"category":"%s","situation":"꼬리","decision":"꼬리","ageYear":24}
              ]
            }
            """.formatted(userId,
                    NodeCategory.EDUCATION, NodeCategory.CAREER, NodeCategory.CAREER, NodeCategory.CAREER, NodeCategory.ETC);

            var res = mockMvc.perform(post("/api/v1/base-lines/bulk")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(withDup))
                    .andExpect(status().isCreated())
                    .andReturn();

            long baseLineId = om.readTree(res.getResponse().getContentAsString()).get("baseLineId").asLong();

            var pivotsRes = mockMvc.perform(get("/api/v1/base-lines/{id}/pivots", baseLineId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pivots.length()").value(2))
                    .andReturn();

            var pivots = om.readTree(pivotsRes.getResponse().getContentAsString()).get("pivots");
            assertThat(pivots.get(0).get("ageYear").asInt()).isEqualTo(20);
            assertThat(pivots.get(1).get("ageYear").asInt()).isEqualTo(22);
        }
    }


    // ===========================
    // 트리 조회 (라인 단위)
    // ===========================
    @Nested
    @DisplayName("트리 조회(라인 단위)")
    class Tree_Read_For_BaseLine {

        // 가장 중요한 테스트: 특정 BaseLine의 전체 트리를 조회하면 Base/Decision이 분리 리스트로 반환된다
        @Test
        @DisplayName("성공 : /{baseLineId}/tree — 결정 라인이 없으면 baseNodes만, decisionNodes는 빈 배열로 반환한다")
        void success_tree_noDecision() throws Exception {
            Long baseLineId = saveAndGetBaseLineId();

            var res = mockMvc.perform(get("/api/v1/base-lines/{id}/tree", baseLineId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.baseNodes.length()").value(4))
                    .andExpect(jsonPath("$.decisionNodes.length()").value(0))
                    .andReturn();

            JsonNode body = om.readTree(res.getResponse().getContentAsString());
            assertThat(body.get("baseNodes").get(0).get("ageYear").asInt()).isEqualTo(18);
            assertThat(body.get("baseNodes").get(3).get("ageYear").asInt()).isEqualTo(24);
        }

        @Test
        @DisplayName("성공 : /{baseLineId}/tree — from-base + next 추가 후 결정 노드가 정렬되어 함께 반환된다")
        void success_tree_withDecisions() throws Exception {
            Long baseLineId = saveAndGetBaseLineId();

            // (가장 많이 사용하는) 피벗: 중간 나이 20을 사용 (헤더/꼬리 제외)
            // 가장 중요한 호출: from-base 생성 요청 (신규 슬림 계약 사용)
            String fromBasePayload = """
                {
                  "userId": %d,
                  "baseLineId": %d,
                  "pivotAge": 20,
                  "selectedAltIndex": 0,
                  "category": "%s",
                  "situation": "분기 시작",
                  "options": ["선택-A"],
                  "selectedIndex": 0
                }
                """.formatted(userId, baseLineId, NodeCategory.CAREER);

                        var fb = mockMvc.perform(post("/api/v1/decision-flow/from-base")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(fromBasePayload))
                                .andExpect(status().isCreated())
                                .andReturn();

            JsonNode fbBody = om.readTree(fb.getResponse().getContentAsString());
            long decisionLineId = fbBody.get("decisionLineId").asLong();
            long headDecisionNodeId = fbBody.get("id").asLong();
            assertThat(decisionLineId).isPositive();
            assertThat(headDecisionNodeId).isPositive();

            // 가장 많이 사용하는 호출: next 추가 (나이 22)
            String nextPayload = """
                {
                  "userId": %d,
                  "parentDecisionNodeId": %d,
                  "decisionLineId": %d,
                  "category": "%s",
                  "situation": "다음 선택",
                  "decision": "선택-A-후속",
                  "ageYear": 22
                }
                """.formatted(userId, headDecisionNodeId, decisionLineId, NodeCategory.CAREER);

                        mockMvc.perform(post("/api/v1/decision-flow/next")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(nextPayload))
                                .andExpect(status().isCreated());

            // 트리 조회 → base 4개 + decision 2개(20,22) 정렬 보장
            var res = mockMvc.perform(get("/api/v1/base-lines/{id}/tree", baseLineId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.baseNodes.length()").value(4))
                    .andExpect(jsonPath("$.decisionNodes.length()").value(2))
                    .andReturn();

            JsonNode tree = om.readTree(res.getResponse().getContentAsString());
            assertThat(tree.get("decisionNodes").get(0).get("ageYear").asInt()).isEqualTo(20);
            assertThat(tree.get("decisionNodes").get(1).get("ageYear").asInt()).isEqualTo(22);
        }


        @Test
        @DisplayName("실패 : 존재하지 않는 baseLineId로 트리 조회 시 404/N002를 반환한다")
        void fail_tree_lineNotFound() throws Exception {
            long unknownId = 9_999_999L;
            mockMvc.perform(get("/api/v1/base-lines/{id}/tree", unknownId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("N002"))
                    .andExpect(jsonPath("$.message").exists());
        }

        // 가장 많이 사용하는: 라인 저장 후 baseLineId 반환
        private Long saveAndGetBaseLineId() throws Exception {
            var res = mockMvc.perform(post("/api/v1/base-lines/bulk")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(sampleLineJson(userId)))
                    .andExpect(status().isCreated())
                    .andReturn();
            return om.readTree(res.getResponse().getContentAsString()).get("baseLineId").asLong();
        }
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
}
