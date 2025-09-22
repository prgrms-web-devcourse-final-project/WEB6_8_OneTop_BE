/**
 * [TEST] BaseLine/BaseNode 저장·조회 (글로벌 에러 응답 검증 포함)
 * - 성공: bulk 저장, 라인 조회, 피벗 조회
 * - 실패: bulk 저장 유효성 실패(nodes < 2) → ApiException(INVALID_INPUT_VALUE) 검증
 */
package com.back.domain.node.controller;

import com.back.domain.node.dto.BaseLineBulkCreateResponse;
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
@DisplayName("BaseLine 플로우(저장/조회) - 통합 테스트(에러포맷 포함)")
public class BaseLineControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper om;
    @Autowired private UserRepository userRepository;

    private Long userId;

    @BeforeAll
    void initUser() {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        User user = User.builder()
                .loginId("login_" + uid)
                .email("user_" + uid + "@test.local")
                .role(Role.GUEST)
                .birthdayAt(LocalDateTime.now().minusYears(25))
                .gender(Gender.M)
                .mbti(Mbti.INTJ)
                .beliefs("NONE")
                .authProvider(AuthProvider.GUEST)
                .nickname("tester-" + uid)
                .build();
        userId = userRepository.save(user).getId();
    }

    @Nested
    @DisplayName("베이스 라인 일괄 저장")
    class BulkCreate {

        @Test
        @DisplayName("T1: 헤더~꼬리 4개 노드 라인을 일괄 저장하면 201과 생성 id 목록을 돌려준다")
        void t1_bulkCreateLine_success() throws Exception {
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
        @DisplayName("T1-ERR: nodes 길이가 2 미만이면 400/INVALID_INPUT_VALUE 반환")
        void t1_err_bulkCreateLine_invalidNodes() throws Exception {
            String bad = """
            { "userId": %d, "nodes": [ { "category":"%s", "situation":"단건", "ageYear":18 } ] }
            """.formatted(userId, NodeCategory.EDUCATION);

            mockMvc.perform(post("/api/v1/base-lines/bulk")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bad))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").value("C001"))
                    .andExpect(jsonPath("$.message").exists());
        }
    }

    @Nested
    @DisplayName("베이스 라인 조회")
    class ReadLine {

        @Test
        @DisplayName("T2: 저장된 라인을 ageYear 오름차순으로 조회할 수 있다")
        void t2_readLine_sortedByAge() throws Exception {
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
        @DisplayName("T3: 피벗 목록은 헤더/꼬리 제외한 나이만 반환한다(20,22)")
        void t3_readPivots_middleOnly() throws Exception {
            Long baseLineId = saveAndGetBaseLineId();

            var res = mockMvc.perform(get("/api/v1/base-lines/{id}/pivots", baseLineId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.baseLineId").value(baseLineId))
                    .andExpect(jsonPath("$.pivots.length()").value(2))
                    .andReturn();

            JsonNode pivots = om.readTree(res.getResponse().getContentAsString()).get("pivots");
            assertThat(pivots.get(0).get("ageYear").asInt()).isEqualTo(20);
            assertThat(pivots.get(1).get("ageYear").asInt()).isEqualTo(22);
        }

        // 저장 → baseLineId 반환
        private Long saveAndGetBaseLineId() throws Exception {
            var res = mockMvc.perform(post("/api/v1/base-lines/bulk")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(sampleLineJson(userId)))
                    .andExpect(status().isCreated())
                    .andReturn();
            return om.readTree(res.getResponse().getContentAsString()).get("baseLineId").asLong();
        }
    }

    private String sampleLineJson(Long uid) {
        return """
        { "userId": %d,
          "nodes": [
            {"category":"%s","situation":"고등학교 졸업","decision":null,"ageYear":18},
            {"category":"%s","situation":"대학 입학","decision":null,"ageYear":20},
            {"category":"%s","situation":"첫 인턴","decision":null,"ageYear":22},
            {"category":"%s","situation":"결말","decision":null,"ageYear":24}
          ]
        }
        """.formatted(uid,
                NodeCategory.EDUCATION, NodeCategory.CAREER, NodeCategory.CAREER, NodeCategory.ETC);
    }
}
