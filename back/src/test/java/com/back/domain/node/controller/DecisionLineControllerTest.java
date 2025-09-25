/**
 * [TEST SUITE] Re:Life — DecisionLine 조회(목록·상세) 통합 테스트
 *
 * 목적
 * - /api/v1/decision-lines?userId=... : 사용자별 결정 라인 목록(요약) 조회 검증
 * - /api/v1/decision-lines/{id}      : 특정 결정 라인 상세(노드 타임라인) 조회 검증
 * - 성공/실패(존재하지 않는 라인) 분류별로 정리
 *
 * 주석 규칙
 * 1) 파일 최상단 요약 주석(본 블록)
 * 2) 가장 중요한 함수 위 한 줄 요약 주석
 * 3) 가장 많이 사용하는 함수/헬퍼 위 한 줄 요약 주석
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Re:Life — DecisionLine 조회(목록·상세) 통합 테스트")
public class DecisionLineControllerTest {

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
                .username("tester-" + uid)
                .build();
        userId = userRepository.save(user).getId();
    }

    // ===========================
    // 목록 조회
    // ===========================
    @Nested
    @DisplayName("결정 라인 목록 조회")
    class ListLines {

        @Test
        @DisplayName("성공 : 한 사용자에 대해 2개 이상의 결정 라인이 생성되면 목록에 각각 요약으로 나타난다")
        void success_listMultipleLines() throws Exception {
            var lineA = startDecisionLine(userId, 0, new String[]{"A1","A2"}, 0);
            var lineB = startDecisionLine(userId, 1, new String[]{"B1","B2"}, 1);

            var res = mockMvc.perform(get("/api/v1/decision-lines")
                            .param("userId", userId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.lines").isArray())
                    .andReturn();

            JsonNode body = om.readTree(res.getResponse().getContentAsString());
            JsonNode lines = body.get("lines");
            assertThat(lines.size()).isGreaterThanOrEqualTo(2);

            JsonNode first = lines.get(0);
            assertThat(first.get("decisionLineId").isNumber()).isTrue();
            assertThat(first.get("baseLineId").isNumber()).isTrue();
            assertThat(first.get("status").asText()).isIn("DRAFT", "COMPLETED", "CANCELLED");
        }

        @Test
        @DisplayName("성공 : 결정 라인이 없으면 빈 배열을 반환한다")
        void success_emptyListWhenNoLines() throws Exception {
            String uid2 = UUID.randomUUID().toString().substring(0, 8);
            Long newUserId = userRepository.save(User.builder()
                            .email("user_" + uid2 + "@test.local")
                            .role(Role.GUEST)
                            .birthdayAt(LocalDateTime.now().minusYears(23))
                            .gender(Gender.F)
                            .mbti(Mbti.ENFP)
                            .beliefs("NONE")
                            .authProvider(AuthProvider.GUEST)
                            .nickname("tester2-" + uid2)
                            .username("tester2-" + uid2)
                            .build())
                    .getId();

            var res = mockMvc.perform(get("/api/v1/decision-lines")
                            .param("userId", newUserId.toString()))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode lines = om.readTree(res.getResponse().getContentAsString()).get("lines");
            assertThat(lines.isArray()).isTrue();
            assertThat(lines.size()).isEqualTo(0);
        }
    }

    // ===========================
    // 상세 조회
    // ===========================
    @Nested
    @DisplayName("결정 라인 상세 조회")
    class DetailLine {

        @Test
        @DisplayName("성공 : 라인 상세는 라인 메타와 노드 배열을 ageYear 오름차순으로 반환한다")
        void success_detailReturnsNodesSorted() throws Exception {
            var head = startDecisionLine(userId, 0, new String[]{"선택A","선택B"}, 0);

            // 가장 많이 사용하는: 다음 결정 1개 추가
            appendNextDecision(head.headDecisionNodeId);

            var res = mockMvc.perform(get("/api/v1/decision-lines/{id}", head.decisionLineId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.decisionLineId").value(head.decisionLineId))
                    .andExpect(jsonPath("$.nodes").isArray())
                    .andReturn();

            JsonNode detail = om.readTree(res.getResponse().getContentAsString());
            JsonNode nodes = detail.get("nodes");
            assertThat(nodes.size()).isGreaterThanOrEqualTo(2);
            int age0 = nodes.get(0).get("ageYear").asInt();
            int age1 = nodes.get(1).get("ageYear").asInt();
            assertThat(age1).isGreaterThan(age0);
            assertThat(nodes.get(0).get("decisionLineId").asLong()).isEqualTo(head.decisionLineId);
        }

        @Test
        @DisplayName("실패 : 존재하지 않는 decisionLineId 상세 조회 시 404/N003를 반환한다")
        void fail_detailNotFound() throws Exception {
            mockMvc.perform(get("/api/v1/decision-lines/{id}", 9_999_999L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("N003"))
                    .andExpect(jsonPath("$.message").exists());
        }
    }

    // ===========================
    // 라인 분리/격리
    // ===========================
    @Nested
    @DisplayName("라인 분리/격리")
    class Separation {

        @Test
        @DisplayName("성공 : 같은 BaseLine의 같은 피벗에서 슬롯0/슬롯1로 시작하면 서로 다른 DecisionLine이 생성된다")
        void success_separateLines_samePivot_diffSlots() throws Exception {
            var base = createBaseLineAndGetPivot(userId, 0);
            var dl1 = fromBaseStartOnExistingBaseLine(userId, base.baseLineId, base.pivotAge,
                    0, new String[]{"A1"}, 0); // 슬롯0 — 단일 옵션
            var dl2 = fromBaseStartOnExistingBaseLine(userId, base.baseLineId, base.pivotAge,
                    1, new String[]{"B1"}, 0); // 슬롯1 — 단일 옵션

            assertThat(dl1.decisionLineId).isNotEqualTo(dl2.decisionLineId);

            var res = mockMvc.perform(get("/api/v1/decision-lines").param("userId", userId.toString()))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode lines = om.readTree(res.getResponse().getContentAsString()).get("lines");
            assertThat(lines.toString()).contains("\"decisionLineId\":" + dl1.decisionLineId);
            assertThat(lines.toString()).contains("\"decisionLineId\":" + dl2.decisionLineId);

            JsonNode d1 = getLineDetail(dl1.decisionLineId);
            JsonNode d2 = getLineDetail(dl2.decisionLineId);
            assertThat(d1.get("decisionLineId").asLong()).isEqualTo(dl1.decisionLineId);
            assertThat(d2.get("decisionLineId").asLong()).isEqualTo(dl2.decisionLineId);
            assertThat(d1.get("nodes").size()).isEqualTo(1);
            assertThat(d2.get("nodes").size()).isEqualTo(1);
        }

        @Test
        @DisplayName("실패 : 같은 BaseLine 같은 피벗 같은 슬롯으로 from-base 재시도시 이미 링크됨(C001)으로 거부된다")
        void fail_sameSlotTwiceRejected_onSameBaseLine() throws Exception {
            var base = createBaseLineAndGetPivot(userId, 0);

            fromBaseStartOnExistingBaseLine(userId, base.baseLineId, base.pivotAge,
                    0, new String[]{"A1"}, 0);

            String again = """
            {
              "userId": %d,
              "baseLineId": %d,
              "pivotAge": %d,
              "selectedAltIndex": 0,
              "category": "%s",
              "situation": "중복 시도",
              "options": ["A1"],
              "selectedIndex": 0
            }
            """.formatted(userId, base.baseLineId, base.pivotAge, NodeCategory.EDUCATION);

            mockMvc.perform(post("/api/v1/decision-flow/from-base")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(again))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("C001"));
        }

        @Test
        @DisplayName("성공 : DL1에 next를 추가해도 DL2에는 영향이 없고 각 상세는 자신의 노드만 포함한다")
        void success_nextAffectsOnlyOwnLine() throws Exception {
            var base = createBaseLineAndGetPivot(userId, 0);
            var dl1 = fromBaseStartOnExistingBaseLine(userId, base.baseLineId, base.pivotAge, 0, new String[]{"A1"}, 0);
            var dl2 = fromBaseStartOnExistingBaseLine(userId, base.baseLineId, base.pivotAge, 1, new String[]{"B1"}, 0);

            appendNextDecision(dl1.headDecisionNodeId);

            JsonNode d1 = getLineDetail(dl1.decisionLineId);
            JsonNode d2 = getLineDetail(dl2.decisionLineId);
            assertThat(d1.get("nodes").size()).isGreaterThanOrEqualTo(2);
            assertThat(d2.get("nodes").size()).isEqualTo(1);
        }

        @Test
        @DisplayName("성공 : 같은 피벗에서 두 옵션을 동시에 정의해도 슬롯0/1 각각 다른 라인으로 생성된다")
        void success_separateLines_samePivot_withTwoOptionsOnBothCalls() throws Exception {
            // 같은 BaseLine, 같은 pivotAge 기준
            var base = createBaseLineAndGetPivot(userId, 0);

            // 첫 호출: 옵션 ["A1","A2"] 정의 + 슬롯0 선택(= altOpt1 링크)
            var dl1 = fromBaseStartOnExistingBaseLine(
                    userId, base.baseLineId, base.pivotAge,
                    0, new String[]{"A1","A2"}, 0
            );

            // 두 번째 호출: 같은 옵션 텍스트 유지 + 슬롯1 선택(= altOpt2 링크)
            var dl2 = fromBaseStartOnExistingBaseLine(
                    userId, base.baseLineId, base.pivotAge,
                    1, new String[]{"A1","A2"}, 1
            );

            // 서로 다른 라인으로 생성
            assertThat(dl1.decisionLineId).isNotEqualTo(dl2.decisionLineId);

            // 상세 각각 1개 노드(헤드)만 포함
            JsonNode d1 = getLineDetail(dl1.decisionLineId);
            JsonNode d2 = getLineDetail(dl2.decisionLineId);
            assertThat(d1.get("nodes").size()).isEqualTo(1);
            assertThat(d2.get("nodes").size()).isEqualTo(1);

            // 한쪽에 next 추가해도 다른 쪽엔 영향 없음
            appendNextDecision(dl1.headDecisionNodeId);
            d1 = getLineDetail(dl1.decisionLineId);
            d2 = getLineDetail(dl2.decisionLineId);
            assertThat(d1.get("nodes").size()).isGreaterThanOrEqualTo(2);
            assertThat(d2.get("nodes").size()).isEqualTo(1);
        }

    }

    // ===========================
    // 헬퍼
    // ===========================

    // 가장 중요한: 베이스라인 생성 → 피벗 선택(pivotOrd) → from-base로 첫 결정 생성
    private HeadLine startDecisionLine(Long uid, int pivotOrd, String[] options2, int selectedIdx) throws Exception {
        var createRes = mockMvc.perform(post("/api/v1/base-lines/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sampleLineJson(uid)))
                .andExpect(status().isCreated())
                .andReturn();
        long baseLineId = om.readTree(createRes.getResponse().getContentAsString()).get("baseLineId").asLong();

        var pivotsRes = mockMvc.perform(get("/api/v1/base-lines/{id}/pivots", baseLineId))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode pivots = om.readTree(pivotsRes.getResponse().getContentAsString()).get("pivots");
        int pivotAge = pivots.get(pivotOrd).get("ageYear").asInt();

        String optionsJson = toJsonArray(options2);
        String fromBaseReq = """
        {
          "userId": %d,
          "baseLineId": %d,
          "pivotAge": %d,
          "selectedAltIndex": %d,
          "category": "%s",
          "situation": "피벗에서 첫 결정",
          "options": %s,
          "selectedIndex": %d
        }
        """.formatted(uid, baseLineId, pivotAge, selectedIdx, NodeCategory.EDUCATION, optionsJson, selectedIdx);

        var fromBaseRes = mockMvc.perform(post("/api/v1/decision-flow/from-base")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fromBaseReq))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode head = om.readTree(fromBaseRes.getResponse().getContentAsString());
        long decisionLineId = head.get("decisionLineId").asLong();
        long headDecisionNodeId = head.get("id").asLong();

        return new HeadLine(decisionLineId, headDecisionNodeId, pivotAge);
    }

    // 가장 많이 사용하는: 기존 BaseLine·pivotAge에서 from-base로 1회 시작
    private HeadLine fromBaseStartOnExistingBaseLine(Long uid, long baseLineId, int pivotAge,
                                                     int selectedAltIndex, String[] options, int selectedIndex) throws Exception {
        String optionsJson = toJsonArray(options);
        String req = """
        {
          "userId": %d,
          "baseLineId": %d,
          "pivotAge": %d,
          "selectedAltIndex": %d,
          "category": "%s",
          "situation": "피벗에서 첫 결정",
          "options": %s,
          "selectedIndex": %d
        }
        """.formatted(uid, baseLineId, pivotAge, selectedAltIndex, NodeCategory.EDUCATION, optionsJson, selectedIndex);

        var res = mockMvc.perform(post("/api/v1/decision-flow/from-base")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode head = om.readTree(res.getResponse().getContentAsString());
        return new HeadLine(head.get("decisionLineId").asLong(), head.get("id").asLong(), head.get("ageYear").asInt());
    }

    // 가장 많이 사용하는: 다음 결정 1개 추가(next) — 자동 다음 피벗
    private void appendNextDecision(long parentDecisionNodeId) throws Exception {
        String nextReq = """
        {
          "userId": %d,
          "parentDecisionNodeId": %d,
          "category": "%s",
          "situation": "다음 선택",
          "options": ["수락","보류","거절"],
          "selectedIndex": 0
        }
        """.formatted(userId, parentDecisionNodeId, NodeCategory.CAREER);

        mockMvc.perform(post("/api/v1/decision-flow/next")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nextReq))
                .andExpect(status().isCreated());
    }

    // 가장 많이 사용하는: 라인 상세 조회(JSON)
    private JsonNode getLineDetail(long decisionLineId) throws Exception {
        var res = mockMvc.perform(get("/api/v1/decision-lines/{id}", decisionLineId))
                .andExpect(status().isOk())
                .andReturn();
        return om.readTree(res.getResponse().getContentAsString());
    }

    // (헬퍼) 문자열 배열을 JSON 배열로 직렬화
    private String toJsonArray(String[] arr) {
        if (arr == null || arr.length == 0) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(arr[i].replace("\"","\\\"")).append('"');
        }
        sb.append(']');
        return sb.toString();
    }

    // 베이스라인 샘플 입력 — fixedChoice=decision 채워 유효성 통과
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
    // 주어진 pivotOrd의 age와 baseLineId를 반환
    private BaseInfo createBaseLineAndGetPivot(Long uid, int pivotOrd) throws Exception {
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
        int pivotAge = pivots.get(pivotOrd).get("ageYear").asInt();

        return new BaseInfo(baseLineId, pivotAge);
    }

    // DTOs
    private record HeadLine(long decisionLineId, long headDecisionNodeId, int ageYear) {}
    private record BaseInfo(long baseLineId, int pivotAge) {}
}
