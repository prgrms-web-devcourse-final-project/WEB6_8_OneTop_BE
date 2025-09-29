/**
 * [TEST SUITE] Re:Life — Decision Flow (from-base · next · cancel · complete) 통합 테스트
 *
 * 목적
 * - /api/v1/decision-flow/from-base 에서 첫 결정 생성, /next 에서 연속 결정 생성, /cancel·/complete 상태 전이 검증
 * - 성공/실패(존재하지 않는 자원, 라인 상태, 규칙 위반, 중복 나이, 피벗 불일치) 분류별로 정리
 *
 */
package com.back.domain.node.controller;

import com.back.domain.node.entity.NodeCategory;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Re:Life — DecisionFlowController from-base · next · cancel · complete 통합 테스트")
public class DecisionFlowControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper om;
    @Autowired private UserRepository userRepository;

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
    // 첫 결정(from-base)
    // ===========================
    @Nested
    @DisplayName("첫 결정(from-base)")
    class FromBase {

        @Test
        @DisplayName("성공 : 유효한 피벗에서 options[2]와 selectedAltIndex로 첫 결정을 생성하면 201과 DecLineDto를 반환한다")
        void success_createFromBase() throws Exception {
            var baseInfo = createBaseLineAndPickFirstPivot(userId);

            String req = """
            {
              "userId": %d,
              "baseLineId": %d,
              "pivotAge": %d,
              "selectedAltIndex": 1,
              "category": "%s",
              "situation": "대학 전공 선택",
              "options": ["전과", "휴학"],
              "selectedIndex": 1
            }
            """.formatted(userId, baseInfo.baseLineId, baseInfo.pivotAge, NodeCategory.EDUCATION);

            var res = mockMvc.perform(post("/api/v1/decision-flow/from-base")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(req))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.type").value("DECISION"))
                    .andExpect(jsonPath("$.decisionLineId").exists())
                    .andExpect(jsonPath("$.baseNodeId").isNumber())
                    .andExpect(jsonPath("$.decision").value("휴학"))
                    .andReturn();

            JsonNode body = om.readTree(res.getResponse().getContentAsString());
            assertThat(body.get("ageYear").asInt()).isEqualTo(baseInfo.pivotAge);
        }

        @Test
        @DisplayName("실패 : 존재하지 않는 베이스라인으로 from-base 요청 시 404/N002를 반환한다")
        void fail_baseLineNotFound_onFromBase() throws Exception {
            var baseInfo = createBaseLineAndPickFirstPivot(userId);
            String req = """
            {
              "userId": %d,
              "baseLineId": 9999999,
              "pivotAge": %d,
              "selectedAltIndex": 0,
              "category": "%s",
              "situation": "무시",
              "options": ["옵션1","옵션2"],
              "selectedIndex": 0
            }
            """.formatted(userId, baseInfo.pivotAge, NodeCategory.RELATIONSHIP);

            mockMvc.perform(post("/api/v1/decision-flow/from-base")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(req))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("N002"))
                    .andExpect(jsonPath("$.message").exists());
        }

        @Test
        @DisplayName("실패 : 잘못된 pivotAge 또는 pivotOrd(범위 밖)면 400/C001을 반환한다")
        void fail_invalidPivot_onFromBase() throws Exception {
            var baseInfo = createBaseLineAndPickFirstPivot(userId);
            String bad = """
            {
              "userId": %d,
              "baseLineId": %d,
              "pivotAge": %d,
              "selectedAltIndex": 0,
              "category": "%s",
              "situation": "무시",
              "options": ["A","B"],
              "selectedIndex": 0
            }
            """.formatted(userId, baseInfo.baseLineId, baseInfo.pivotAge + 99, NodeCategory.ETC);

            mockMvc.perform(post("/api/v1/decision-flow/from-base")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bad))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("C001"));
        }

        @Test
        @DisplayName("실패 : 동일 분기 슬롯을 두 번 링크하려 하면 400/C001(이미 링크됨)을 반환한다")
        void fail_branchSlotAlreadyLinked() throws Exception {
            var baseInfo = createBaseLineAndPickFirstPivot(userId);

            String first = """
            {
              "userId": %d,
              "baseLineId": %d,
              "pivotAge": %d,
              "selectedAltIndex": 0,
              "category": "%s",
              "situation": "첫 분기",
              "options": ["A","B"],
              "selectedIndex": 0
            }
            """.formatted(userId, baseInfo.baseLineId, baseInfo.pivotAge, NodeCategory.CAREER);

            mockMvc.perform(post("/api/v1/decision-flow/from-base")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(first))
                    .andExpect(status().isCreated());

            String secondSameSlot = first.replace("\"첫 분기\"", "\"중복 분기\"");
            mockMvc.perform(post("/api/v1/decision-flow/from-base")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(secondSameSlot))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("C001"));
        }
    }

    // ===========================
    // 다음 결정(next)
    // ===========================
    @Nested
    @DisplayName("다음 결정(next)")
    class NextDecision {

        @Test
        @DisplayName("성공 : 부모에서 다음 피벗 나이로 생성하면 201과 DecLineDto(부모 id/다음 나이)를 반환한다")
        void success_createNextDecision() throws Exception {
            var head = startDecisionFromBase(userId);
            long parentId = head.decisionNodeId;

            String nextReq = """
            {
              "userId": %d,
              "parentDecisionNodeId": %d,
              "category": "%s",
              "situation": "인턴 후 진로 기로",
              "options": ["수락","보류","거절"],
              "selectedIndex": 0
            }
            """.formatted(userId, parentId, NodeCategory.CAREER);

            var res = mockMvc.perform(post("/api/v1/decision-flow/next")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(nextReq))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.parentId").value(parentId))
                    .andReturn();

            JsonNode body = om.readTree(res.getResponse().getContentAsString());
            assertThat(body.get("ageYear").asInt()).isGreaterThan(head.ageYear);
            assertThat(body.get("decision").asText()).isEqualTo("수락");
        }

        @Test
        @DisplayName("실패 : 존재하지 않는 부모 결정 노드로 next 요청 시 404/N001을 반환한다")
        void fail_parentDecisionNotFound() throws Exception {
            String nextReq = """
            {
              "userId": %d,
              "parentDecisionNodeId": 9999999,
              "category": "%s",
              "situation": "무시",
              "options": ["x","y"],
              "selectedIndex": 0
            }
            """.formatted(userId, NodeCategory.CAREER);

            mockMvc.perform(post("/api/v1/decision-flow/next")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(nextReq))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("N001"))
                    .andExpect(jsonPath("$.message").exists());
        }

        @Test
        @DisplayName("실패 : 동일 나이로 재생성 시도(부모 ageYear와 같음)면 400/C001을 반환한다")
        void fail_duplicateAgeOnLine() throws Exception {
            var head = startDecisionFromBase(userId);

            String nextReq = """
            {
              "userId": %d,
              "parentDecisionNodeId": %d,
              "category": "%s",
              "situation": "동일나이 재결정",
              "ageYear": %d
            }
            """.formatted(userId, head.decisionNodeId, NodeCategory.ETC, head.ageYear);

            mockMvc.perform(post("/api/v1/decision-flow/next")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(nextReq))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("C001"));
        }

        @Test
        @DisplayName("실패 : 부모 결정 나이보다 작은 나이로 next 요청 시 400/C001을 반환한다")
        void fail_nextAgeLessThanParent() throws Exception {
            var head = startDecisionFromBase(userId);
            int invalidAge = head.ageYear - 1;

            String nextReq = """
            {
              "userId": %d,
              "parentDecisionNodeId": %d,
              "category": "%s",
              "situation": "나이 감소",
              "ageYear": %d
            }
            """.formatted(userId, head.decisionNodeId, NodeCategory.ETC, invalidAge);

            mockMvc.perform(post("/api/v1/decision-flow/next")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(nextReq))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("C001"));
        }
    }

    // ===========================
    // 상태 전이(cancel / complete)
    // ===========================
    @Nested
    @DisplayName("상태 전이(cancel/complete)")
    class Lifecycle {

        @Test
        @DisplayName("성공 : 취소 요청 시 라인 상태가 CANCELLED로 바뀐다")
        void success_cancel() throws Exception {
            var head = startDecisionFromBase(userId);
            mockMvc.perform(post("/api/v1/decision-flow/{decisionLineId}/cancel", head.decisionLineId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.decisionLineId").value(head.decisionLineId))
                    .andExpect(jsonPath("$.status").value("CANCELLED"));
        }

        @Test
        @DisplayName("성공 : 완료 요청 시 라인 상태가 COMPLETED로 바뀐다")
        void success_complete() throws Exception {
            var head = startDecisionFromBase(userId);
            mockMvc.perform(post("/api/v1/decision-flow/{decisionLineId}/complete", head.decisionLineId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.decisionLineId").value(head.decisionLineId))
                    .andExpect(jsonPath("$.status").value("COMPLETED"));
        }

        @Test
        @DisplayName("실패 : 존재하지 않는 decisionLineId 취소/완료 시 404/N003를 반환한다")
        void fail_lineNotFound_onLifecycle() throws Exception {
            mockMvc.perform(post("/api/v1/decision-flow/{decisionLineId}/cancel", 9999999L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("N003"));
            mockMvc.perform(post("/api/v1/decision-flow/{decisionLineId}/complete", 9999999L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("N003"));
        }

        @Test
        @DisplayName("실패 : 완료/취소된 라인에서 next 시도 시 400/C001(line is locked)을 반환한다")
        void fail_nextAfterLocked() throws Exception {
            var head = startDecisionFromBase(userId);
            mockMvc.perform(post("/api/v1/decision-flow/{decisionLineId}/complete", head.decisionLineId))
                    .andExpect(status().isOk());

            String nextReq = """
            {
              "userId": %d,
              "parentDecisionNodeId": %d,
              "category": "%s",
              "situation": "완료 후 시도",
              "options": ["x","y"],
              "selectedIndex": 0
            }
            """.formatted(userId, head.decisionNodeId, NodeCategory.CAREER);

            mockMvc.perform(post("/api/v1/decision-flow/next")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(nextReq))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("C001"));

            var head2 = startDecisionFromBase(userId);
            mockMvc.perform(post("/api/v1/decision-flow/{decisionLineId}/cancel", head2.decisionLineId))
                    .andExpect(status().isOk());

            String nextReq2 = """
            {
              "userId": %d,
              "parentDecisionNodeId": %d,
              "category": "%s",
              "situation": "취소 후 시도"
            }
            """.formatted(userId, head2.decisionNodeId, NodeCategory.ETC);

            mockMvc.perform(post("/api/v1/decision-flow/next")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(nextReq2))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("C001"));
        }
    }


    // 세계선 포크(fork) — DecisionNode에서 다른 선택지로 새 DecisionLine 생성
    @Nested
    @DisplayName("세계선 포크(fork)")
    class ForkBranch {

        // 가장 중요한: 헤드 결정 노드에서 다른 선택을 골라 새 라인을 만든다
        @Test
        @DisplayName("성공 : 헤드 결정에서 다른 선택지로 fork 하면 새 decisionLineId와 교체된 decision을 반환한다")
        void success_forkFromHead_changesSelection_createsNewLine() throws Exception {
            var head = startDecisionFromBase(userId); // 기존 헬퍼: options ["선택 A","선택 B"], selectedIndex=0

            String req = """
        {
          "userId": %d,
          "parentDecisionNodeId": %d,
          "targetOptionIndex": 1,
          "keepUntilParent": true,
          "lineTitle": "fork-1"
        }
        """.formatted(userId, head.decisionNodeId);

            // 가장 많이 쓰는 호출: /decision-flow/fork 엔드포인트 POST
            var res = mockMvc.perform(post("/api/v1/decision-flow/fork")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(req))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.type").value("DECISION"))
                    .andExpect(jsonPath("$.decisionLineId").exists())
                    .andExpect(jsonPath("$.id").exists())
                    .andReturn();

            JsonNode body = om.readTree(res.getResponse().getContentAsString());
            long newLineId = body.get("decisionLineId").asLong();
            String decision = body.get("decision").asText();
            int age = body.get("ageYear").asInt();

            assertThat(newLineId).isNotEqualTo(head.decisionLineId);
            assertThat(decision).isEqualTo("선택 B");
            assertThat(age).isEqualTo(head.ageYear);
            assertThat(body.get("parentId").isNull()).isTrue();
        }

        @Test
        @DisplayName("성공 : 같은 노드에서 여러 번 fork 하면 각기 다른 decisionLineId가 발급된다(무한 세계선)")
        void success_multipleForksFromSameNode() throws Exception {
            var head = startDecisionFromBase(userId);

            String fork0 = """
        {"userId": %d, "parentDecisionNodeId": %d, "targetOptionIndex": 0, "keepUntilParent": true}
        """.formatted(userId, head.decisionNodeId);
            String fork1 = """
        {"userId": %d, "parentDecisionNodeId": %d, "targetOptionIndex": 1, "keepUntilParent": true}
        """.formatted(userId, head.decisionNodeId);

            var r0 = mockMvc.perform(post("/api/v1/decision-flow/fork")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fork0))
                    .andExpect(status().isCreated())
                    .andReturn();
            var r1 = mockMvc.perform(post("/api/v1/decision-flow/fork")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fork1))
                    .andExpect(status().isCreated())
                    .andReturn();

            long line0 = om.readTree(r0.getResponse().getContentAsString()).get("decisionLineId").asLong();
            long line1 = om.readTree(r1.getResponse().getContentAsString()).get("decisionLineId").asLong();

            assertThat(line0).isNotEqualTo(head.decisionLineId);
            assertThat(line1).isNotEqualTo(head.decisionLineId);
            assertThat(line0).isNotEqualTo(line1);
        }

        @Test
        @DisplayName("성공 : fork로 생성된 새 라인을 /next로 계속 이어갈 수 있다")
        void success_forkThenContinueWithNext() throws Exception {
            var head = startDecisionFromBase(userId);

            String fork = """
        {"userId": %d, "parentDecisionNodeId": %d, "targetOptionIndex": 1, "keepUntilParent": true}
        """.formatted(userId, head.decisionNodeId);

            var forkRes = mockMvc.perform(post("/api/v1/decision-flow/fork")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fork))
                    .andExpect(status().isCreated())
                    .andReturn();

            JsonNode forkNode = om.readTree(forkRes.getResponse().getContentAsString());
            long forkHeadId = forkNode.get("id").asLong();

            String nextReq = """
        {
          "userId": %d,
          "parentDecisionNodeId": %d,
          "category": "%s",
          "situation": "fork 이후 진행",
          "options": ["수락","보류","거절"],
          "selectedIndex": 2
        }
        """.formatted(userId, forkHeadId, NodeCategory.CAREER);

            var nextRes = mockMvc.perform(post("/api/v1/decision-flow/next")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(nextReq))
                    .andExpect(status().isCreated())
                    .andReturn();

            JsonNode nextNode = om.readTree(nextRes.getResponse().getContentAsString());
            assertThat(nextNode.get("parentId").asLong()).isEqualTo(forkHeadId);
            assertThat(nextNode.get("decision").asText()).isEqualTo("거절");
        }

        @Test
        @DisplayName("실패 : targetOptionIndex가 옵션 수를 초과하면 400/C001을 반환한다")
        void fail_targetOptionIndexOutOfRange() throws Exception {
            var head = startDecisionFromBase(userId); // 헤드는 옵션 2개

            String bad = """
        {"userId": %d, "parentDecisionNodeId": %d, "targetOptionIndex": 2, "keepUntilParent": true}
        """.formatted(userId, head.decisionNodeId);

            mockMvc.perform(post("/api/v1/decision-flow/fork")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bad))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("C001"));
        }

        @Test
        @DisplayName("실패 : 옵션이 없는 결정 노드에서 fork 시도 시 400/C001을 반환한다")
        void fail_forkOnNodeWithoutOptions() throws Exception {
            // 헤드 생성(옵션 2개) 후, 옵션 없이 다음 노드 생성
            var head = startDecisionFromBase(userId);

            String nextNoOptions = """
        {
          "userId": %d,
          "parentDecisionNodeId": %d,
          "category": "%s",
          "situation": "옵션 없는 노드",
          "ageYear": null
        }
        """.formatted(userId, head.decisionNodeId, NodeCategory.ETC);

            var nextRes = mockMvc.perform(post("/api/v1/decision-flow/next")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(nextNoOptions))
                    .andExpect(status().isCreated())
                    .andReturn();

            long nodeId = om.readTree(nextRes.getResponse().getContentAsString()).get("id").asLong();

            String fork = """
        {"userId": %d, "parentDecisionNodeId": %d, "targetOptionIndex": 0, "keepUntilParent": true}
        """.formatted(userId, nodeId);

            mockMvc.perform(post("/api/v1/decision-flow/fork")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(fork))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("C001"));
        }

        @Test
        @DisplayName("실패 : 존재하지 않는 결정 노드로 fork 시 404/N001을 반환한다")
        void fail_parentDecisionNotFound_onFork() throws Exception {
            String req = """
        {"userId": %d, "parentDecisionNodeId": 9999999, "targetOptionIndex": 0, "keepUntilParent": true}
        """.formatted(userId);

            mockMvc.perform(post("/api/v1/decision-flow/fork")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(req))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("N001"))
                    .andExpect(jsonPath("$.message").exists());
        }
    }


    // ===========================
    // 공통 헬퍼
    // ===========================

    // 베이스라인 생성 → 첫 피벗에서 options[2] 입력/선택으로 결정 시작 → 헤드 결정 정보 반환
    private HeadDecision startDecisionFromBase(Long uid) throws Exception {
        var createRes = mockMvc.perform(post("/api/v1/base-lines/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sampleLineJson(uid)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode created = om.readTree(createRes.getResponse().getContentAsString());
        long baseLineId = created.get("baseLineId").asLong();

        var pivotsRes = mockMvc.perform(get("/api/v1/base-lines/{id}/pivots", baseLineId))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode pivots = om.readTree(pivotsRes.getResponse().getContentAsString()).get("pivots");
        int pivotAge = pivots.get(0).get("ageYear").asInt();

        String fromBaseReq = """
        {
          "userId": %d,
          "baseLineId": %d,
          "pivotAge": %d,
          "selectedAltIndex": 0,
          "category": "%s",
          "situation": "피벗에서 첫 결정",
          "options": ["선택 A","선택 B"],
          "selectedIndex": 0
        }
        """.formatted(uid, baseLineId, pivotAge, NodeCategory.EDUCATION);

        var fromBaseRes = mockMvc.perform(post("/api/v1/decision-flow/from-base")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fromBaseReq))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode head = om.readTree(fromBaseRes.getResponse().getContentAsString());
        long decisionLineId = head.get("decisionLineId").asLong();
        long decisionNodeId = head.get("id").asLong();
        int headAge = head.get("ageYear").asInt();

        return new HeadDecision(decisionLineId, decisionNodeId, headAge);
    }

    // 베이스라인을 만들고 첫 피벗(age) 정보를 반환
    private BaseInfo createBaseLineAndPickFirstPivot(Long uid) throws Exception {
        var res = mockMvc.perform(post("/api/v1/base-lines/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sampleLineJson(uid)))
                .andExpect(status().isCreated())
                .andReturn();
        long baseLineId = om.readTree(res.getResponse().getContentAsString()).get("baseLineId").asLong();

        var pivotsRes = mockMvc.perform(get("/api/v1/base-lines/{id}/pivots", baseLineId))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode pivots = om.readTree(pivotsRes.getResponse().getContentAsString()).get("pivots");
        int pivotAge = pivots.get(0).get("ageYear").asInt();
        return new BaseInfo(baseLineId, pivotAge);
    }

    // 정상 입력 샘플 JSON — 헤더/중간/중간/꼬리 4노드 (fixedChoice=decision을 채워 유효성 통과)
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
                NodeCategory.EDUCATION, NodeCategory.EDUCATION, NodeCategory.CAREER, NodeCategory.ETC);
    }

    // 헤드 결정 정보 DTO
    private record HeadDecision(long decisionLineId, long decisionNodeId, int ageYear) {}

    // 피벗 정보 DTO
    private record BaseInfo(long baseLineId, int pivotAge) {}
}
