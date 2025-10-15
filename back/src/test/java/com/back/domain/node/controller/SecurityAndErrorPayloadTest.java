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

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Re:Life — 인증/권한 & 오류 페이로드")
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
public class SecurityAndErrorPayloadTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper om;
    @Autowired private UserRepository userRepository;

    private User userA, userB;

    @BeforeEach
    void initUsers() {
        userA = createUser("A");
        userB = createUser("B");
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearCtx() { SecurityContextHolder.clearContext(); }

    @Test
    @DisplayName("실패 : 미인증으로 /base-lines/mine 호출 시 401")
    void fail_unauthenticated_401_withFilters() throws Exception {
        mockMvc.perform(get("/api/v1/base-lines/mine"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("실패 : 타인 결정 라인 취소 시도 시 403")
    void fail_forbidden_cancelOthersLine_403() throws Exception {
        // A로 라인 생성
        long decisionLineId = startDecisionLine(userA.getId(), userA);

        // B로 취소 시도 → 403 (POST + csrf + B 인증)
        mockMvc.perform(post("/api/v1/decision-flow/{decisionLineId}/cancel", decisionLineId)
                        .with(user(new CustomUserDetails(userB)))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("성공 : 오류 페이로드 공통 필드(code/message/path/timestamp) 포함")
    void success_error_payload_shape() throws Exception {
        // A 인증 후 존재하지 않는 노드 조회
        var res = mockMvc.perform(get("/api/v1/base-lines/nodes/{nodeId}", 9_999_999L)
                        .with(user(new CustomUserDetails(userA))))
                .andExpect(status().isNotFound())
                .andReturn();

        JsonNode err = om.readTree(res.getResponse().getContentAsString());
        assertThat(err.has("code")).isTrue();
        assertThat(err.has("message")).isTrue();
        assertThat(err.has("path")).isTrue();
        assertThat(err.has("timestamp")).isTrue();
    }

    // ---------- 헬퍼 ----------

    // 베이스라인 만들고 첫 피벗에서 from-base까지 한 번에
    private long startDecisionLine(Long uid, User principal) throws Exception {
        // 1) base bulk (POST + csrf + 인증)
        var created = mockMvc.perform(post("/api/v1/base-lines/bulk")
                        .with(user(new CustomUserDetails(principal)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sampleLineJson(uid)))
                .andExpect(status().isCreated())
                .andReturn();
        long baseLineId = om.readTree(created.getResponse().getContentAsString()).get("baseLineId").asLong();

        // 2) pivots (GET + 인증)
        var pivotsRes = mockMvc.perform(get("/api/v1/base-lines/{id}/pivots", baseLineId)
                        .with(user(new CustomUserDetails(principal))))
                .andExpect(status().isOk())
                .andReturn();
        int pivotAge = om.readTree(pivotsRes.getResponse().getContentAsString())
                .get("pivots").get(0).get("ageYear").asInt();

        // 3) from-base (POST + csrf + 인증)
        String fromBase = """
        {
          "userId": %d, "baseLineId": %d, "pivotAge": %d, "selectedAltIndex": 0,
          "category": "%s", "situation": "헤드", "options": ["A","B"], "selectedIndex": 0
        }
        """.formatted(uid, baseLineId, pivotAge, NodeCategory.EDUCATION);

        var head = mockMvc.perform(post("/api/v1/decision-flow/from-base")
                        .with(user(new CustomUserDetails(principal)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fromBase))
                .andExpect(status().isCreated())
                .andReturn();
        return om.readTree(head.getResponse().getContentAsString()).get("decisionLineId").asLong();
    }

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

    private User createUser(String tag) {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder()
                .email("user" + tag + "_" + uid + "@test.local")
                .role(Role.USER)
                .birthdayAt(LocalDateTime.now().minusYears(25))
                .gender(tag.equals("A") ? Gender.M : Gender.F)
                .mbti(Mbti.INTJ)
                .beliefs("NONE")
                .authProvider(AuthProvider.LOCAL)
                .nickname("tester-" + tag + "-" + uid)
                .username("name-" + tag + "-" + uid)
                .build());
    }
}
