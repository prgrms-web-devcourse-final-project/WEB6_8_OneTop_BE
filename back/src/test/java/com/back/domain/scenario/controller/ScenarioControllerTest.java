package com.back.domain.scenario.controller;

import com.back.domain.node.dto.decision.DecisionNodeNextRequest;
import com.back.domain.node.entity.NodeCategory;
import com.back.domain.scenario.dto.*;
import com.back.domain.scenario.entity.ScenarioStatus;
import com.back.domain.scenario.entity.Type;
import com.back.domain.scenario.service.ScenarioService;
import com.back.domain.user.entity.User;
import com.back.global.common.PageResponse;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import com.back.global.security.CustomUserDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ScenarioController 통합 테스트.
 * 세션 기반 인증을 활성화하고 MockMvc의 .with(user())를 통해
 * 인증된 사용자로 API를 테스트합니다. Service는 모킹하여 테스트합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc // Security 필터 활성화하여 @AuthenticationPrincipal 정상 동작
@ActiveProfiles("test")
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("ScenarioController 통합 테스트")
class ScenarioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ScenarioService scenarioService;

    // Mock 사용자 생성
    private CustomUserDetails mockUserDetails;

    @BeforeEach
    void setUp() {
        // Mock User 생성 (ID = 1L)
        User mockUser = User.builder()
                .email("test@example.com")
                .username("테스트사용자")
                .nickname("테스트닉네임")
                .birthdayAt(LocalDateTime.of(1990, 1, 1, 0, 0))
                .role(com.back.domain.user.entity.Role.USER)
                .build();

        // BaseEntity의 id는 Reflection으로 설정
        try {
            java.lang.reflect.Field idField = mockUser.getClass().getSuperclass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(mockUser, 1L);

            // Reflection이 제대로 작동했는지 검증
            Long verifyId = (Long) idField.get(mockUser);
            if (verifyId == null || !verifyId.equals(1L)) {
                throw new RuntimeException("Failed to set user ID via Reflection, got: " + verifyId);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to set mock user ID", e);
        }

        mockUserDetails = new CustomUserDetails(mockUser);
    }

    @Nested
    @DisplayName("시나리오 생성")
    class CreateScenario {

        @Test
        @DisplayName("성공 - 정상적인 시나리오 생성 요청")
        void createScenario_성공() throws Exception {
            // Given
            Long decisionLineId = 100L;
            ScenarioCreateRequest request = new ScenarioCreateRequest(decisionLineId);

            ScenarioStatusResponse mockResponse = new ScenarioStatusResponse(
                    1001L,
                    ScenarioStatus.PENDING,
                    "시나리오 생성이 시작되었습니다."
            );

            DecisionNodeNextRequest lastNode= new DecisionNodeNextRequest(
                    1L,
                    100L,
                    NodeCategory.CAREER,
                    "커리어 방향 선택",
                    28,
                    List.of("이직", "석사과정", "창업"),
                    2,
                    1,
                    "리스크 감수"
            );

            given(scenarioService.createScenario(eq(1L), any(ScenarioCreateRequest.class),
                    any(DecisionNodeNextRequest.class)))
                    .willReturn(mockResponse);

            // request를 JSON으로 직렬화 (컨트롤러 @RequestPart("scenario")가 이걸 받음)
            byte[] scenarioJson = objectMapper.writeValueAsBytes(request);

            MockMultipartFile scenarioPart = new MockMultipartFile(
                    "scenario",
                    "", "application/json",
                    scenarioJson
            );
            MockMultipartFile lastDecisionPart = new MockMultipartFile(
                    "lastDecision",
                    "", "application/json",
                    objectMapper.writeValueAsBytes(lastNode)
            );

            // When & Then
            mockMvc.perform(multipart("/api/v1/scenarios")
                            .file(scenarioPart)
                            .file(lastDecisionPart)
                            .with(req -> { req.setMethod("POST"); return req; })
                            .with(csrf())
                            .with(user(mockUserDetails)))
                    .andDo(print())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.scenarioId").value(1001))
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.message").value("시나리오 생성이 시작되었습니다."));
        }

        @Test
        @DisplayName("실패 - 잘못된 요청 데이터 (null decisionLineId) - multipart")
        void createScenario_실패_잘못된요청_multipart() throws Exception {
            // Given: decisionLineId=null인 잘못된 JSON을 그대로 scenario 파트에 실어 보냄
            byte[] invalidScenarioJson = "{\"decisionLineId\":null}".getBytes();

            MockMultipartFile scenarioPart = new MockMultipartFile(
                    "scenario", "", "application/json", invalidScenarioJson
            );

            // When & Then
            mockMvc.perform(multipart("/api/v1/scenarios")
                            .file(scenarioPart)
                            .with(req -> { req.setMethod("POST"); return req; })
                            .with(csrf())
                            .with(user(mockUserDetails)))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패 - Service 예외 발생 - multipart")
        void createScenario_실패_Service예외_multipart() throws Exception {
            // Given
            ScenarioCreateRequest request = new ScenarioCreateRequest(999L);

            DecisionNodeNextRequest lastNode = new DecisionNodeNextRequest(
                    1L,
                    456L,
                    NodeCategory.CAREER,
                    "커리어 방향 선택",
                    28,
                    List.of("이직", "석사과정", "창업"),
                    2,
                    1,
                    "리스크 감수"
            );

            // 서비스 예외 스텁
            given(scenarioService.createScenario(
                    eq(1L),
                    any(ScenarioCreateRequest.class),
                    any(DecisionNodeNextRequest.class)
            )).willThrow(new ApiException(ErrorCode.DECISION_LINE_NOT_FOUND));

            // 멀티파트 파트 구성
            MockMultipartFile scenarioPart = new MockMultipartFile(
                    "scenario", "", "application/json",
                    objectMapper.writeValueAsBytes(request)
            );
            MockMultipartFile lastDecisionPart = new MockMultipartFile(
                    "lastDecision", "", "application/json",
                    objectMapper.writeValueAsBytes(lastNode)
            );

            // When & Then
            mockMvc.perform(multipart("/api/v1/scenarios")
                            .file(scenarioPart)
                            .file(lastDecisionPart)
                            .with(req -> { req.setMethod("POST"); return req; })
                            .with(csrf())
                            .with(user(mockUserDetails)))
                    .andDo(print())
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("시나리오 상태 조회")
    class GetScenarioStatus {

        @Test
        @DisplayName("성공 - 유효한 시나리오 상태 조회")
        void getScenarioStatus_성공() throws Exception {
            // Given
            Long scenarioId = 1001L;
            ScenarioStatusResponse mockResponse = new ScenarioStatusResponse(
                    scenarioId,
                    ScenarioStatus.COMPLETED,
                    "시나리오 생성이 완료되었습니다."
            );

            given(scenarioService.getScenarioStatus(scenarioId, 1L))
                    .willReturn(mockResponse);

            // When & Then
            mockMvc.perform(get("/api/v1/scenarios/{scenarioId}/status", scenarioId)
                            .with(user(mockUserDetails)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.scenarioId").value(scenarioId))
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.message").value("시나리오 생성이 완료되었습니다."));
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 시나리오")
        void getScenarioStatus_실패_없는시나리오() throws Exception {
            // Given
            Long scenarioId = 999L;

            given(scenarioService.getScenarioStatus(scenarioId, 1L))
                    .willThrow(new ApiException(ErrorCode.SCENARIO_NOT_FOUND));

            // When & Then
            mockMvc.perform(get("/api/v1/scenarios/{scenarioId}/status", scenarioId)
                            .with(user(mockUserDetails)))
                    .andDo(print())
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("시나리오 상세 조회")
    class GetScenarioDetail {

        @Test
        @DisplayName("성공 - 완료된 시나리오 상세 조회")
        void getScenarioDetail_성공() throws Exception {
            // Given
            Long scenarioId = 1001L;

            List<ScenarioTypeDto> indicators = List.of(
                    new ScenarioTypeDto(Type.경제, 90, "창업 성공으로 높은 경제적 성취"),
                    new ScenarioTypeDto(Type.행복, 85, "자신이 원하는 일을 하며 성취감 높음"),
                    new ScenarioTypeDto(Type.관계, 75, "업무로 인해 개인 관계에 다소 소홀"),
                    new ScenarioTypeDto(Type.직업, 95, "창업가로서 최고 수준의 직업 만족도"),
                    new ScenarioTypeDto(Type.건강, 70, "스트레스로 인한 건강 관리 필요")
            );

            ScenarioDetailResponse mockResponse = new ScenarioDetailResponse(
                    scenarioId,
                    ScenarioStatus.COMPLETED,
                    "스타트업 CEO",
                    85,
                    "성공적인 창업으로 안정적인 수익 창출",
                    "창업 초기 어려움을 극복하고 지속가능한 비즈니스 모델을 구축했습니다.",
                    "https://example.com/scenario-image.jpg",
                    LocalDateTime.now().minusDays(1),
                    indicators
            );

            given(scenarioService.getScenarioDetail(scenarioId, 1L))
                    .willReturn(mockResponse);

            // When & Then
            mockMvc.perform(get("/api/v1/scenarios/info/{scenarioId}", scenarioId)
                            .with(user(mockUserDetails)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.scenarioId").value(scenarioId))
                    .andExpect(jsonPath("$.job").value("스타트업 CEO"))
                    .andExpect(jsonPath("$.total").value(85))
                    .andExpect(jsonPath("$.indicators").isArray())
                    .andExpect(jsonPath("$.indicators.length()").value(5));
        }
    }

    @Nested
    @DisplayName("시나리오 타임라인 조회")
    class GetScenarioTimeline {

        @Test
        @DisplayName("성공 - 시나리오 타임라인 조회")
        void getScenarioTimeline_성공() throws Exception {
            // Given
            Long scenarioId = 1001L;

            List<TimelineResponse.TimelineEvent> events = List.of(
                    new TimelineResponse.TimelineEvent(2025, "창업 시작"),
                    new TimelineResponse.TimelineEvent(2027, "첫 투자 유치"),
                    new TimelineResponse.TimelineEvent(2030, "IPO 성공")
            );

            TimelineResponse mockResponse = new TimelineResponse(scenarioId, events);

            given(scenarioService.getScenarioTimeline(scenarioId, 1L))
                    .willReturn(mockResponse);

            // When & Then
            mockMvc.perform(get("/api/v1/scenarios/{scenarioId}/timeline", scenarioId)
                            .with(user(mockUserDetails)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.scenarioId").value(scenarioId))
                    .andExpect(jsonPath("$.events").isArray())
                    .andExpect(jsonPath("$.events[0].year").value(2025))
                    .andExpect(jsonPath("$.events[0].title").value("창업 시작"));
        }
    }

    @Nested
    @DisplayName("베이스라인 목록 조회")
    class GetBaselines {

        @Test
        @DisplayName("성공 - 사용자 베이스라인 목록 조회")
        void getBaselines_성공() throws Exception {
            // Given
            List<BaselineListResponse> content = List.of(
                    new BaselineListResponse(
                            200L,
                            "대학 졸업 이후",
                            List.of("진로", "교육"),
                            LocalDateTime.now().minusMonths(6)
                    ),
                    new BaselineListResponse(
                            201L,
                            "첫 직장 입사",
                            List.of("직업", "성장"),
                            LocalDateTime.now().minusMonths(3)
                    )
            );

            Page<BaselineListResponse> page = new PageImpl<>(content, PageRequest.of(0, 10), content.size());
            PageResponse<BaselineListResponse> mockPageResponse = PageResponse.of(page);

            given(scenarioService.getBaselines(eq(1L), any()))
                    .willReturn(mockPageResponse);

            // When & Then
            mockMvc.perform(get("/api/v1/scenarios/baselines")
                            .param("page", "0")
                            .param("size", "10")
                            .with(user(mockUserDetails)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isArray())
                    .andExpect(jsonPath("$.page").value(1))  // 1-based pagination
                    .andExpect(jsonPath("$.size").value(10))
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.totalPages").value(1))
                    .andExpect(jsonPath("$.items[0].baselineId").value(200))
                    .andExpect(jsonPath("$.items[0].title").value("대학 졸업 이후"));
        }
    }

    @Nested
    @DisplayName("시나리오 비교")
    class CompareScenarios {

        @Test
        @DisplayName("성공 - 시나리오 비교 분석")
        void compareScenarios_성공() throws Exception {
            // Given
            Long baseId = 1001L;
            Long compareId = 1002L;

            List<ScenarioCompareResponse.IndicatorComparison> indicators = List.of(
                    new ScenarioCompareResponse.IndicatorComparison(Type.경제, 90, 80, "창업이 대기업보다 경제적으로 유리"),
                    new ScenarioCompareResponse.IndicatorComparison(Type.행복, 85, 70, "창업을 통한 더 높은 성취감"),
                    new ScenarioCompareResponse.IndicatorComparison(Type.관계, 75, 85, "대기업에서 더 안정적인 인간관계"),
                    new ScenarioCompareResponse.IndicatorComparison(Type.직업, 95, 75, "창업가로서 더 높은 직업 만족도"),
                    new ScenarioCompareResponse.IndicatorComparison(Type.건강, 70, 80, "대기업에서 더 나은 워라밸")
            );

            ScenarioCompareResponse mockResponse = new ScenarioCompareResponse(
                    baseId,
                    compareId,
                    "창업 경로가 전반적으로 더 도전적이지만 성취감과 경제적 보상이 큽니다.",
                    indicators
            );

            given(scenarioService.compareScenarios(baseId, compareId, 1L))
                    .willReturn(mockResponse);

            // When & Then
            mockMvc.perform(get("/api/v1/scenarios/compare/{baseId}/{compareId}", baseId, compareId)
                            .with(user(mockUserDetails)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.baseScenarioId").value(baseId))
                    .andExpect(jsonPath("$.compareScenarioId").value(compareId))
                    .andExpect(jsonPath("$.overallAnalysis").exists())
                    .andExpect(jsonPath("$.indicators").isArray());
        }
    }
}