/**
 * [TEST SUITE] Re:Life — DecisionFlow 동시성 & DVCS 해석(PINNED/FOLLOW/OVERRIDE) 통합 테스트 (보안 일괄 적용)
 *
 * 코드 흐름 요약
 * - 보안 필터 활성화(@AutoConfigureMockMvc(addFilters=true)) 전제에서 모든 요청에 인증(.with(authed(userId))) 적용
 * - 모든 POST 요청에는 CSRF(.with(csrf())) 필수 적용
 * - 동시성 테스트: 같은 pivot·같은 슬롯 from-base 동시 2회 → 1 성공(201) / 1 실패(400/C001) 보장
 * - DVCS 해석 테스트: FOLLOW → base/edit 커밋 반영, PINNED(root) 고정, OVERRIDE로 덮어쓰기까지 effectiveDecision 검증
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
@DisplayName("Re:Life — 동시성 & DVCS 해석 통합 테스트 (보안 적용)")
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
public class DecisionFlowConcurrencyAndDvcsTest {

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
        setAuth(new CustomUserDetails(userRepository.findById(userId).orElseThrow()));
    }

    @AfterEach
    void clearCtx() { SecurityContextHolder.clearContext(); }

    // ===========================
    // 동시성: 같은 슬롯 경합
    // ===========================
    @Test
    @DisplayName("성공 : 같은 pivotAge·같은 슬롯에서 from-base 동시 2요청 → 하나는 201, 하나는 400/C001")
    void success_race_onSameBranchSlot_onlyOneWins() throws Exception {
        // given: baseline + pivotAge
        var base = createBaseLineAndGetPivot(userId, 0);
        String reqJson = """
        {
          "userId": %d,
          "baseLineId": %d,
          "pivotAge": %d,
          "selectedAltIndex": 0,
          "category": "%s",
          "situation": "동시성 테스트",
          "options": ["A1","A2"],
          "selectedIndex": 0
        }
        """.formatted(userId, base.baseLineId, base.pivotAge, NodeCategory.EDUCATION);

        // when: 동시에 두 번 호출 (각 호출에 인증 + CSRF 부착)
        ExecutorService es = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);
        Callable<Integer> task = () -> {
            latch.await(2, TimeUnit.SECONDS);
            var mvcRes = mockMvc.perform(post("/api/v1/decision-flow/from-base")
                            .with(authed(userId))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(reqJson))
                    .andReturn();
            return mvcRes.getResponse().getStatus();
        };
        Future<Integer> f1 = es.submit(task);
        Future<Integer> f2 = es.submit(task);
        latch.countDown();
        int s1 = f1.get(5, TimeUnit.SECONDS);
        int s2 = f2.get(5, TimeUnit.SECONDS);
        es.shutdownNow();

        // then: 하나는 201, 다른 하나는 400
        assertThat(List.of(s1, s2)).contains(201);
        assertThat(List.of(s1, s2)).contains(400);
    }

    // ===========================
    // DVCS: FOLLOW → edit → PINNED → OVERRIDE
    // ===========================
    @Test
    @DisplayName("성공 : FOLLOW는 최신 커밋을, PINNED(root)는 과거를, OVERRIDE는 라인 고유 버전을 각각 표시한다")
    void success_dvcs_followPinnedOverride_resolution() throws Exception {
        // given: baseline with pivot(20: '대학 입학')
        var base = createBaseLineAndGetPivot(userId, 0);
        int pivotAge = base.pivotAge; // e.g., 20
        String head = """
        {
          "userId": %d,
          "baseLineId": %d,
          "pivotAge": %d,
          "selectedAltIndex": 0,
          "category": "%s",
          "situation": "대학 입학",
          "options": ["입학","휴학"],
          "selectedIndex": 0
        }
        """.formatted(userId, base.baseLineId, pivotAge, NodeCategory.EDUCATION);
        var headRes = mockMvc.perform(post("/api/v1/decision-flow/from-base")
                        .with(authed(userId))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(head))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode headNode = om.readTree(headRes.getResponse().getContentAsString());
        long decisionLineId = headNode.get("decisionLineId").asLong();
        long decisionNodeId  = headNode.get("id").asLong();
        String originalDecision = headNode.get("effectiveDecision").isNull()
                ? headNode.get("decision").asText()
                : headNode.get("effectiveDecision").asText();

        // when: base/edit로 pivotAge의 decision을 "편입"으로 변경 → 새 커밋 생성
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
          "contentHash": "hash-1",
          "message": "edit-1"
        }
        """.formatted(base.baseLineId, getMainBranchId(base.baseLineId), pivotAge, NodeCategory.EDUCATION);
        mockMvc.perform(post("/api/v1/dvcs/base/edit")
                        .with(authed(userId))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(editReq))
                .andExpect(status().isOk());

        // then(FOLLOW): 상세 조회 시 해당 age의 effectiveDecision이 "편입"으로 보임
        JsonNode detailAfterEdit = getLineDetail(decisionLineId);
        String effectiveAfterEdit = findDecisionAtAge(detailAfterEdit, pivotAge);
        assertThat(effectiveAfterEdit).isEqualTo("편입");

        // and: root 커밋 id를 찾아 PINNED(root)로 고정하면 과거 값(originalDecision)로 보임
        long rootCommitId = getRootCommitId(base.baseLineId);
        String pinReq = """
        {"decisionNodeId": %d, "policy": "PINNED", "pinnedCommitId": %d}
        """.formatted(decisionNodeId, rootCommitId);
        mockMvc.perform(post("/api/v1/dvcs/decision/policy")
                        .with(authed(userId))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pinReq))
                .andExpect(status().isOk());

        JsonNode detailPinned = getLineDetail(decisionLineId);
        String effectivePinned = findDecisionAtAge(detailPinned, pivotAge);
        assertThat(effectivePinned).isEqualTo(originalDecision);

        // and: OVERRIDE로 전환하여 "자체-결정"으로 덮으면 그 값이 표시됨
        String overrideReq = """
        {
          "decisionNodeId": %d,
          "promoteToBase": false,
          "category": "%s",
          "situation": "라인 고유",
          "decision": "자체-결정",
          "optionsJson": null,
          "description": "override",
          "contentHash": "hash-ovr",
          "message": null
        }
        """.formatted(decisionNodeId, NodeCategory.EDUCATION);
        mockMvc.perform(post("/api/v1/dvcs/decision/edit")
                        .with(authed(userId))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(overrideReq))
                .andExpect(status().isOk());

        JsonNode detailOverride = getLineDetail(decisionLineId);
        String effectiveOverride = findDecisionAtAge(detailOverride, pivotAge);
        assertThat(effectiveOverride).isEqualTo("자체-결정");
    }

    // 가장 중요한 함수 한줄 요약: SecurityContext 인증 토큰 세팅
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

    // 가장 많이 사용하는 함수 호출 한줄 요약: 특정 라인 상세 JSON 조회(인증)
    private JsonNode getLineDetail(long decisionLineId) throws Exception {
        var res = mockMvc.perform(get("/api/v1/decision-lines/{id}", decisionLineId)
                        .with(authed(userId)))
                .andExpect(status().isOk())
                .andReturn();
        return om.readTree(res.getResponse().getContentAsString());
    }

    // 가장 많이 사용하는 함수 호출 한줄 요약: 상세 JSON에서 특정 ageYear의 effectiveDecision/decision 추출
    private String findDecisionAtAge(JsonNode detail, int age) {
        for (JsonNode n : detail.get("nodes")) {
            if (n.get("ageYear").asInt() == age) {
                if (!n.get("effectiveDecision").isNull()) return n.get("effectiveDecision").asText();
                return n.get("decision").asText();
            }
        }
        return null;
    }

    // 가장 많이 사용하는 함수 호출 한줄 요약: main 브랜치 id 조회(인증)
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

    // 가장 중요한 함수 한줄 요약: root(부모 없는) 커밋 id 조회(인증)
    private long getRootCommitId(long baseLineId) throws Exception {
        var res = mockMvc.perform(get("/api/v1/dvcs/branches/{baseLineId}", baseLineId)
                        .with(authed(userId)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode arr = om.readTree(res.getResponse().getContentAsString());
        for (JsonNode b : arr) {
            if (!"main".equals(b.get("name").asText())) continue;
            for (JsonNode c : b.get("commits")) {
                if (c.get("parentCommitId").isNull()) return c.get("commitId").asLong();
            }
        }
        throw new IllegalStateException("root commit not found");
    }

    // (자주 쓰는) 베이스라인 샘플 입력
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

    // 가장 많이 사용하는 함수 한줄 요약: 베이스라인 생성 후 첫 pivotAge 반환(인증/CSRF)
    private BaseInfo createBaseLineAndGetPivot(Long uid, int pivotOrd) throws Exception {
        var res = mockMvc.perform(post("/api/v1/base-lines/bulk")
                        .with(authed(uid))
                        .with(csrf())
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
        int pivotAge = pivots.get(pivotOrd).get("ageYear").asInt();

        return new BaseInfo(baseLineId, pivotAge);
    }

    // DTO
    private record BaseInfo(long baseLineId, int pivotAge) {}
}
