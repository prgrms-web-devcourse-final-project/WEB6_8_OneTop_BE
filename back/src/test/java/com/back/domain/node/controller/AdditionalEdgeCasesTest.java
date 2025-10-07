/**
 * [TEST SUITE] Re:Life — 추가 엣지 케이스(계약/정렬/DVCS/경계) — 보안/헤드선택 버그 수정 통합본
 *
 * 코드 흐름 요약
 * - addFilters=true 환경에서 모든 요청에 인증(.with(authed(userId))) 적용, 모든 POST에 CSRF(.with(csrf())) 적용.
 * - “현재 헤드(말단) 노드 선택” 로직을 childrenIds 비어있음(말단) → age 최댓값 폴백 순으로 일관화.
 * - 기존 getHeadNodeIdFromDetail가 루트(부모 null, age 최소)를 반환하던 버그를 제거해 next 호출 시 중복 나이 충돌(400/C001) 방지.
 * - 트리 정렬/링크 무결성, DVCS, 입력 경계 테스트들을 보안 일관성 + 올바른 부모 선택으로 검증.
 */
package com.back.domain.node.controller;

import com.back.domain.node.entity.NodeCategory;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Re:Life — 추가 엣지 케이스(계약/정렬/DVCS/경계)")
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
public class AdditionalEdgeCasesTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper om;
    @Autowired private UserRepository userRepository;

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
        SecurityContextHolder.clearContext(); // per-request post processor로 인증 부착
    }

    @AfterEach
    void clearCtx() { SecurityContextHolder.clearContext(); }

    // 가장 많이 사용하는 함수 호출 한줄 요약: uid로 인증 RequestPostProcessor 생성
    private RequestPostProcessor authed(Long uid) {
        var me = userRepository.findById(uid).orElseThrow();
        return user(new CustomUserDetails(me));
    }

    // ===========================
    // 입력 경계/일관성
    // ===========================
    @Nested
    @DisplayName("입력 경계/일관성")
    class InputBoundary {

        @Test
        @DisplayName("실패 : selectedIndex가 options 길이를 초과하면 400/C001")
        void fail_selectedIndex_outOfRange_onFromBase() throws Exception {
            var base = createBaseLineAndPickFirstPivot(userId);
            String req = """
            {
              "userId": %d,
              "baseLineId": %d,
              "pivotAge": %d,
              "selectedAltIndex": 0,
              "category": "%s",
              "situation": "선택 인덱스 초과",
              "options": ["A","B"],
              "selectedIndex": 2
            }
            """.formatted(userId, base.baseLineId, base.pivotAge, NodeCategory.EDUCATION);

            mockMvc.perform(post("/api/v1/decision-flow/from-base")
                            .with(csrf())
                            .with(authed(userId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(req))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("C001"));
        }

        @Test
        @DisplayName("실패 : selectedAltIndex 범위 초과면 400/C001")
        void fail_selectedAltIndex_outOfRange() throws Exception {
            var base = createBaseLineAndPickFirstPivot(userId);
            String req = """
            {
              "userId": %d,
              "baseLineId": %d,
              "pivotAge": %d,
              "selectedAltIndex": 2,
              "category": "%s",
              "situation": "alt 인덱스 초과",
              "options": ["A"],
              "selectedIndex": 0
            }
            """.formatted(userId, base.baseLineId, base.pivotAge, NodeCategory.EDUCATION);

            mockMvc.perform(post("/api/v1/decision-flow/from-base")
                            .with(csrf())
                            .with(authed(userId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(req))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("C001"));
        }

        @Test
        @DisplayName("실패 : options가 null 또는 빈 배열이면 400/C001")
        void fail_options_null_or_empty() throws Exception {
            var base = createBaseLineAndPickFirstPivot(userId);

            String nullOptions = """
            {
              "userId": %d, "baseLineId": %d, "pivotAge": %d, "selectedAltIndex": 0,
              "category": "%s", "situation": "옵션 null", "options": null, "selectedIndex": 0
            }
            """.formatted(userId, base.baseLineId, base.pivotAge, NodeCategory.CAREER);

            String emptyOptions = """
            {
              "userId": %d, "baseLineId": %d, "pivotAge": %d, "selectedAltIndex": 0,
              "category": "%s", "situation": "옵션 빈배열", "options": [], "selectedIndex": 0
            }
            """.formatted(userId, base.baseLineId, base.pivotAge, NodeCategory.CAREER);

            mockMvc.perform(post("/api/v1/decision-flow/from-base")
                            .with(csrf())
                            .with(authed(userId))
                            .contentType(MediaType.APPLICATION_JSON).content(nullOptions))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("C001"));

            mockMvc.perform(post("/api/v1/decision-flow/from-base")
                            .with(csrf())
                            .with(authed(userId))
                            .contentType(MediaType.APPLICATION_JSON).content(emptyOptions))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("C001"));
        }

        @Test
        @DisplayName("실패 : base bulk에 매우 큰 ageYear/소수 ageYear 전달 시 400/C001")
        void fail_abnormal_age_in_base_bulk() throws Exception {
            String abnormal = """
            { "userId": %d,
              "nodes": [
                {"category":"%s","situation":"헤더","decision":"헤더","ageYear":0},
                {"category":"%s","situation":"비정상","decision":"비정상","ageYear":200},
                {"category":"%s","situation":"소수","decision":"소수","ageYear":20.5},
                {"category":"%s","situation":"꼬리","decision":"꼬리","ageYear":120}
              ]
            }
            """.formatted(userId, NodeCategory.EDUCATION, NodeCategory.CAREER, NodeCategory.CAREER, NodeCategory.ETC);

            mockMvc.perform(post("/api/v1/base-lines/bulk")
                            .with(csrf())
                            .with(authed(userId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(abnormal))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("C001"));
        }
    }

    // ===========================
    // 트리 정렬/링크 무결성
    // ===========================
    @Nested
    @DisplayName("트리 정렬/링크 무결성")
    class TreeIntegrity {

        // 한줄 요약: /tree의 decisionNodes를 "라인별"로 묶어 각 라인 안에서 age asc(동률 id asc)와 링크 무결성을 검증
        @Test
        @DisplayName("성공 : /tree의 decisionNodes는 라인별로 age asc(동률 id asc) 정렬 & 링크 필드 일관")
        void success_tree_mergeSorted_and_linkIntegrity() throws Exception {
            var base = createBaseLineAndPickFirstPivot(userId); // pivot 예: 20

            // 동일 피벗에서 슬롯만 다르게, 옵션 텍스트는 동일하게 유지(계약상 요구)
            String[] CANONICAL = {"A1","A2"};
            long dlA = fromBaseStart(userId, base.baseLineId, base.pivotAge, 0, CANONICAL, 0).decisionLineId;
            long dlB = fromBaseStart(userId, base.baseLineId, base.pivotAge, 1, CANONICAL, 1).decisionLineId;

            appendNextOnLineHead(dlA);
            appendNextOnLineHead(dlB);

            var treeRes = mockMvc.perform(get("/api/v1/base-lines/{id}/tree", base.baseLineId)
                            .with(authed(userId)))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode tree = om.readTree(treeRes.getResponse().getContentAsString());

            JsonNode baseNodes = tree.get("baseNodes");
            JsonNode decisionNodes = tree.get("decisionNodes");
            assertThat(baseNodes.isArray()).isTrue();
            assertThat(decisionNodes.isArray()).isTrue();
            assertThat(decisionNodes.size()).isGreaterThanOrEqualTo(4);

            // 라인별로 묶어서 각 라인 안에서만 정렬 검증
            Map<Long, List<JsonNode>> byLine = new LinkedHashMap<>();
            for (JsonNode n : decisionNodes) {
                long dl = n.get("decisionLineId").asLong();
                byLine.computeIfAbsent(dl, k -> new ArrayList<>()).add(n);

                // 링크 무결성(필드 존재/타입) — 전체 노드에 대해 공통 검사
                assertThat(n.get("decisionLineId").isNumber()).isTrue();
                if (!n.get("parentId").isNull())    assertThat(n.get("parentId").isNumber()).isTrue();
                if (!n.get("baseNodeId").isNull())  assertThat(n.get("baseNodeId").isNumber()).isTrue();
            }

            // 각 라인 안에서 age asc, 동률이면 id asc
            for (Map.Entry<Long, List<JsonNode>> e : byLine.entrySet()) {
                int prevAge = Integer.MIN_VALUE;
                long prevId  = Long.MIN_VALUE;

                for (JsonNode n : e.getValue()) {
                    int age = n.get("ageYear").asInt();
                    long id = n.get("id").asLong();

                    // age 오름차순
                    assertThat(age).isGreaterThanOrEqualTo(prevAge);
                    // 동률이면 id 오름차순
                    if (age == prevAge) {
                        assertThat(id).isGreaterThanOrEqualTo(prevId);
                    }

                    prevAge = age;
                    prevId  = id;
                }
            }
        }

    }

    // ===========================
    // DVCS 추가 시나리오
    // ===========================
    @Nested
    @DisplayName("DVCS 추가 시나리오")
    class DvcsMore {

        @Test
        @DisplayName("실패 : 존재하지 않는 커밋 id로 PINNED 고정 시 400/C001")
        void fail_pin_toUnknownCommit_returnsC001() throws Exception {
            var base = createBaseLineAndPickFirstPivot(userId);
            var head = fromBaseStart(userId, base.baseLineId, base.pivotAge, 0, new String[]{"A"}, 0);
            String req = """
            {"decisionNodeId": %d, "policy": "PINNED", "pinnedCommitId": 9999999}
            """.formatted(head.headDecisionNodeId);

            mockMvc.perform(post("/api/v1/dvcs/decision/policy")
                            .with(csrf())
                            .with(authed(userId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(req))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("C001"));
        }

        @Test
        @DisplayName("성공 : OVERRIDE 후 FOLLOW로 복귀하면 최신 커밋 해석으로 되돌아간다")
        void success_override_then_follow_back_toLatest() throws Exception {
            var base = createBaseLineAndPickFirstPivot(userId);
            var head = fromBaseStart(userId, base.baseLineId, base.pivotAge, 0, new String[]{"입학","휴학"}, 0);

            long mainBranchId = getMainBranchId(base.baseLineId);
            String editReq = """
            {
              "baseLineId": %d,
              "branchId": %d,
              "ageYear": %d,
              "category": "%s",
              "situation": "대학 편입",
              "decision": "편입",
              "optionsJson": null,
              "description": "편입으로 수정",
              "contentHash": "hash-x",
              "message": "edit-x"
            }
            """.formatted(base.baseLineId, mainBranchId, base.pivotAge, NodeCategory.EDUCATION);
            mockMvc.perform(post("/api/v1/dvcs/base/edit")
                            .with(csrf())
                            .with(authed(userId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(editReq))
                    .andExpect(status().isOk());

            String overrideReq = """
            {
              "decisionNodeId": %d,
              "promoteToBase": false,
              "category": "%s",
              "situation": "라인 고유",
              "decision": "자체-결정",
              "optionsJson": null,
              "description": "ovr",
              "contentHash": "hash-ovr",
              "message": null
            }
            """.formatted(head.headDecisionNodeId, NodeCategory.EDUCATION);
            mockMvc.perform(post("/api/v1/dvcs/decision/edit")
                            .with(csrf())
                            .with(authed(userId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(overrideReq))
                    .andExpect(status().isOk());

            String followReq = """
            {"decisionNodeId": %d, "policy": "FOLLOW"}
            """.formatted(head.headDecisionNodeId);
            mockMvc.perform(post("/api/v1/dvcs/decision/policy")
                            .with(csrf())
                            .with(authed(userId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(followReq))
                    .andExpect(status().isOk());

            JsonNode detail = getLineDetail(head.decisionLineId);
            assertThat(findDecisionAtAge(detail, base.pivotAge)).isEqualTo("편입");
        }

        @Test
        @DisplayName("성공 : decision/edit(promoteToBase=true) 승격 시 다른 FOLLOW 라인도 즉시 반영")
        void success_decisionEdit_withPromoteToBase_updatesFollowers() throws Exception {
            var base = createBaseLineAndPickFirstPivot(userId);
            var a = fromBaseStart(userId, base.baseLineId, base.pivotAge, 0, new String[]{"A1","A2"}, 0);
            var b = fromBaseStart(userId, base.baseLineId, base.pivotAge, 1, new String[]{"A1","A2"}, 1);

            String promote = """
            {
              "decisionNodeId": %d,
              "promoteToBase": true,
              "category": "%s",
              "situation": "승격",
              "decision": "승격-결정",
              "optionsJson": null,
              "description": "promote",
              "contentHash": "hash-promote",
              "message": "promote-commit"
            }
            """.formatted(a.headDecisionNodeId, NodeCategory.EDUCATION);
            mockMvc.perform(post("/api/v1/dvcs/decision/edit")
                            .with(csrf())
                            .with(authed(userId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(promote))
                    .andExpect(status().isOk());

            JsonNode detailB = getLineDetail(b.decisionLineId);
            assertThat(findDecisionAtAge(detailB, base.pivotAge)).isEqualTo("승격-결정");
        }
    }

    // ===========================
    // 피벗 고갈 시 next
    // ===========================
    @Nested
    @DisplayName("피벗 고갈 시 next 실패")
    class NoMorePivot {

        @Test
        @DisplayName("실패 : 마지막 피벗 이후 next 요청 시 400/C001이며 노드 수 증가 없음")
        void fail_next_whenNoMorePivots_returnsC001_and_noNodeCreated() throws Exception {
            var base = createBaseLineAndPickFirstPivot(userId); // 첫 피벗 예: 20
            var head = fromBaseStart(userId, base.baseLineId, base.pivotAge, 0, new String[]{"A"}, 0);

            appendNextOnLineHead(head.decisionLineId); // 22세 생성(헤드를 부모로)

            JsonNode before = getLineDetail(head.decisionLineId);
            int beforeCnt = before.get("nodes").size();

            String nextReq = """
            {"userId": %d, "parentDecisionNodeId": %d, "category": "%s", "situation": "추가 시도"}
            """.formatted(userId, getHeadNodeId(head.decisionLineId), NodeCategory.ETC);

            mockMvc.perform(post("/api/v1/decision-flow/next")
                            .with(csrf())
                            .with(authed(userId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(nextReq))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("C001"));

            JsonNode after = getLineDetail(head.decisionLineId);
            int afterCnt = after.get("nodes").size();
            assertThat(afterCnt).isEqualTo(beforeCnt);
        }
    }

    // ===========================
    // 공통 헬퍼들
    // ===========================

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

    private BaseInfo createBaseLineAndPickFirstPivot(Long uid) throws Exception {
        var res = mockMvc.perform(post("/api/v1/base-lines/bulk")
                        .with(csrf())
                        .with(authed(uid))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sampleLineJson(uid)))
                .andExpect(status().isCreated())
                .andReturn();
        long baseLineId = om.readTree(res.getResponse().getContentAsString()).get("baseLineId").asLong();

        var pivotsRes = mockMvc.perform(get("/api/v1/base-lines/{id}/pivots", baseLineId)
                        .with(authed(uid)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode pivots = om.readTree(pivotsRes.getResponse().getContentAsString()).get("pivots");
        int pivotAge = pivots.get(0).get("ageYear").asInt();
        return new BaseInfo(baseLineId, pivotAge);
    }

    private Head fromBaseStart(Long uid, long baseLineId, int pivotAge,
                               int selectedAltIndex, String[] options, int selectedIndex) throws Exception {
        String optionsJson = toJsonArray(options);
        String req = """
        {
          "userId": %d, "baseLineId": %d, "pivotAge": %d, "selectedAltIndex": %d,
          "category": "%s", "situation": "헤드", "options": %s, "selectedIndex": %d
        }
        """.formatted(uid, baseLineId, pivotAge, selectedAltIndex, NodeCategory.EDUCATION, optionsJson, selectedIndex);
        var res = mockMvc.perform(post("/api/v1/decision-flow/from-base")
                        .with(csrf())
                        .with(authed(uid))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(req))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode head = om.readTree(res.getResponse().getContentAsString());
        return new Head(head.get("decisionLineId").asLong(), head.get("id").asLong());
    }

    // 가장 많이 사용하는 함수 호출 한줄 요약: 현재 헤드(말단)를 부모로 next 1회 추가
    private void appendNextOnLineHead(long decisionLineId) throws Exception {
        JsonNode detail = getLineDetail(decisionLineId);
        long parentId = getTailNodeIdFromDetail(detail); // ★ 루트가 아닌 “말단”을 부모로
        String nextReq = """
        {"userId": %d, "parentDecisionNodeId": %d, "category": "%s", "situation": "다음", "options": ["X","Y"], "selectedIndex": 0}
        """.formatted(userId, parentId, NodeCategory.CAREER);
        mockMvc.perform(post("/api/v1/decision-flow/next")
                        .with(csrf())
                        .with(authed(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nextReq))
                .andExpect(status().isCreated());
    }

    // 가장 중요한 함수 한줄 요약: 라인 상세에서 “현재 헤드(말단)” 노드 id 반환
    private long getTailNodeIdFromDetail(JsonNode detail) {
        long tailId = -1L;
        int maxAge = Integer.MIN_VALUE;

        // 1) childrenIds가 비어있는 노드(말단) 우선
        for (JsonNode n : detail.get("nodes")) {
            boolean noChildren = !n.has("childrenIds") || n.get("childrenIds").isEmpty();
            int age = n.get("ageYear").asInt();
            if (noChildren && age >= maxAge) {
                maxAge = age;
                tailId = n.get("id").asLong();
            }
        }
        // 2) 폴백: childrenIds가 전송되지 않는 구현을 대비해 age 최댓값 기준
        if (tailId == -1L) {
            for (JsonNode n : detail.get("nodes")) {
                int age = n.get("ageYear").asInt();
                if (age >= maxAge) {
                    maxAge = age;
                    tailId = n.get("id").asLong();
                }
            }
        }
        return tailId;
    }

    private long getHeadNodeId(long decisionLineId) throws Exception {
        // 테스트 호환성 유지: “헤드” 용어를 기존 호출부에서 사용하므로 내부적으로 말단을 반환
        return getTailNodeIdFromDetail(getLineDetail(decisionLineId));
    }

    private JsonNode getLineDetail(long decisionLineId) throws Exception {
        var res = mockMvc.perform(get("/api/v1/decision-lines/{id}", decisionLineId)
                        .with(authed(userId)))
                .andExpect(status().isOk())
                .andReturn();
        return om.readTree(res.getResponse().getContentAsString());
    }

    private String findDecisionAtAge(JsonNode detail, int age) {
        for (JsonNode n : detail.get("nodes")) {
            if (n.get("ageYear").asInt() == age) {
                return n.get("effectiveDecision").isNull()
                        ? n.get("decision").asText()
                        : n.get("effectiveDecision").asText();
            }
        }
        return null;
    }

    private long getMainBranchId(long baseLineId) throws Exception {
        var res = mockMvc.perform(get("/api/v1/dvcs/branches/{baseLineId}", baseLineId)
                        .with(authed(userId)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode arr = om.readTree(res.getResponse().getContentAsString());
        for (JsonNode b : arr) {
            if ("main".equals(b.get("name").asText())) return b.get("branchId").asLong();
        }
        throw new IllegalStateException("main branch not found");
    }

    private String toJsonArray(String[] arr) {
        if (arr == null || arr.length == 0) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(arr[i].replace("\"", "\\\"")).append('"');
        }
        sb.append(']');
        return sb.toString();
    }

    private record BaseInfo(long baseLineId, int pivotAge) {}
    private record Head(long decisionLineId, long headDecisionNodeId) {}
}
