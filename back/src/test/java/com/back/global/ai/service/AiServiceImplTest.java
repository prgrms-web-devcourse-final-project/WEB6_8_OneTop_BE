package com.back.global.ai.service;

import com.back.domain.node.entity.*;
import com.back.domain.scenario.entity.Scenario;
import com.back.domain.scenario.entity.SceneType;
import com.back.domain.scenario.entity.Type;
import com.back.domain.scenario.repository.SceneTypeRepository;
import com.back.domain.user.entity.User;
import com.back.global.ai.client.text.TextAiClient;
import com.back.global.ai.dto.result.BaseScenarioResult;
import com.back.global.ai.dto.result.DecisionScenarioResult;
import com.back.global.ai.exception.AiParsingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;

/**
 * AiServiceImpl 클래스의 단위 테스트
 * Mock을 사용하여 AI 클라이언트 및 의존성을 격리한 테스트를 수행합니다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AiServiceImpl 테스트")
class AiServiceImplTest {

    @Mock
    private TextAiClient textAiClient;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private SceneTypeRepository sceneTypeRepository;

    @InjectMocks
    private AiServiceImpl aiService;

    private User testUser;
    private BaseLine testBaseLine;
    private DecisionLine testDecisionLine;
    private Scenario testScenario;

    @BeforeEach
    void setUp() {
        // 테스트용 사용자 생성 (ID 제거, LocalDateTime 사용)
        testUser = User.builder()
                .email("test@example.com")
                .nickname("testUser")
                .birthdayAt(LocalDateTime.of(1995, 5, 15, 0, 0))
                .build();

        // 테스트용 베이스라인 생성 (ID 제거)
        testBaseLine = BaseLine.builder()
                .user(testUser)
                .title("대학 졸업 후 진로")
                .build();

        // 테스트용 베이스노드 생성 및 추가 (ID 제거)
        BaseNode baseNode1 = BaseNode.builder()
                .user(testUser)
                .baseLine(testBaseLine)
                .ageYear(22)
                .category(NodeCategory.EDUCATION)
                .situation("대학교 4학년, 졸업 준비")
                .decision("대기업 취업 준비")
                .build();

        BaseNode baseNode2 = BaseNode.builder()
                .user(testUser)
                .baseLine(testBaseLine)
                .ageYear(25)
                .category(NodeCategory.CAREER)
                .situation("직장 3년차, 업무 적응 완료")
                .decision("현재 직장 유지")
                .build();

        testBaseLine.getBaseNodes().addAll(List.of(baseNode1, baseNode2));

        // 테스트용 결정라인 생성 (ID 제거, title 필드 제거)
        testDecisionLine = DecisionLine.builder()
                .user(testUser)
                .baseLine(testBaseLine)
                .status(DecisionLineStatus.DRAFT)
                .build();

        // 테스트용 결정노드 생성 및 추가 (ID 제거)
        DecisionNode decisionNode1 = DecisionNode.builder()
                .user(testUser)
                .decisionLine(testDecisionLine)
                .ageYear(23)
                .category(NodeCategory.EDUCATION)
                .situation("대학 졸업 후 진로 고민")
                .decision("대학원 진학")
                .build();

        DecisionNode decisionNode2 = DecisionNode.builder()
                .user(testUser)
                .decisionLine(testDecisionLine)
                .ageYear(25)
                .category(NodeCategory.CAREER)
                .situation("대학원 2년차, 연구 심화")
                .decision("박사과정 진학")
                .build();

        testDecisionLine.getDecisionNodes().addAll(List.of(decisionNode1, decisionNode2));

        // 테스트용 시나리오 생성 (ID 제거)
        testScenario = Scenario.builder()
                .user(testUser)
                .baseLine(testBaseLine)
                .job("대기업 과장")
                .summary("안정적인 직장생활")
                .description("대기업에서 3년간 근무하며 안정적인 성과를 보이고 있습니다.")
                .total(250)
                .build();
    }

    @Nested
    @DisplayName("베이스 시나리오 생성")
    class GenerateBaseScenario {

        @Test
        @DisplayName("성공 - 정상적인 BaseLine으로 베이스 시나리오 생성")
        void generateBaseScenario_성공() throws Exception {
            // Given
            String mockAiResponse = """
                {
                    "job": "대기업 과장",
                    "summary": "안정적인 직장생활을 유지하는 삶",
                    "description": "현재 직장에서 꾸준히 성과를 내며 안정적인 삶을 이어가고 있습니다.",
                    "total": 250,
                    "baselineTitle": "대기업 직장인의 삶",
                    "indicators": [
                        {"type": "경제", "point": 50, "analysis": "평균적인 소득 수준"},
                        {"type": "행복", "point": 50, "analysis": "무난한 행복 지수"},
                        {"type": "관계", "point": 50, "analysis": "적당한 인간관계"},
                        {"type": "직업", "point": 50, "analysis": "안정적인 직업"},
                        {"type": "건강", "point": 50, "analysis": "보통 건강 상태"}
                    ],
                    "timelineTitles": {
                        "2017": "대학 졸업",
                        "2020": "직장 적응"
                    }
                }
                """;

            // Record 생성자 사용 (builder 대신)
            BaseScenarioResult expectedResult = new BaseScenarioResult(
                    "대기업 과장",                                              // job
                    "안정적인 직장생활을 유지하는 삶",                                   // summary
                    "현재 직장에서 꾸준히 성과를 내며 안정적인 삶을 이어가고 있습니다.",       // description
                    250,                                                   // total
                    Map.of("2017", "대학 졸업", "2020", "직장 적응"),             // timelineTitles
                    "대기업 직장인의 삶",                                          // baselineTitle
                    Map.of(Type.경제, 50, Type.행복, 50),                      // indicatorScores
                    Map.of(Type.경제, "평균적 소득", Type.행복, "보통 행복")           // indicatorAnalysis
            );

            given(textAiClient.generateText(anyString()))
                    .willReturn(CompletableFuture.completedFuture(mockAiResponse));
            given(objectMapper.readValue(eq(mockAiResponse), eq(BaseScenarioResult.class)))
                    .willReturn(expectedResult);

            // When
            CompletableFuture<BaseScenarioResult> result = aiService.generateBaseScenario(testBaseLine);

            // Then
            assertThat(result).isCompleted();
            BaseScenarioResult actualResult = result.join();
            assertThat(actualResult.job()).isEqualTo("대기업 과장");
            assertThat(actualResult.summary()).isEqualTo("안정적인 직장생활을 유지하는 삶");
            assertThat(actualResult.total()).isEqualTo(250);
            assertThat(actualResult.baselineTitle()).isEqualTo("대기업 직장인의 삶");

            verify(textAiClient).generateText(anyString());
            verify(objectMapper).readValue(eq(mockAiResponse), eq(BaseScenarioResult.class));
        }

        @Test
        @DisplayName("실패 - BaseLine이 null인 경우")
        void generateBaseScenario_실패_널입력() {
            // When & Then
            assertThatThrownBy(() -> aiService.generateBaseScenario(null))
                    .isInstanceOf(CompletableFuture.class);

            CompletableFuture<BaseScenarioResult> result = aiService.generateBaseScenario(null);
            assertThatThrownBy(result::join)
                    .hasCauseInstanceOf(AiParsingException.class)
                    .hasMessageContaining("BaseLine cannot be null");

            verifyNoInteractions(textAiClient);
        }

        @Test
        @DisplayName("실패 - JSON 파싱 에러")
        void generateBaseScenario_실패_JSON파싱에러() throws Exception {
            // Given
            String invalidJsonResponse = "{ invalid json }";

            given(textAiClient.generateText(anyString()))
                    .willReturn(CompletableFuture.completedFuture(invalidJsonResponse));
            given(objectMapper.readValue(eq(invalidJsonResponse), eq(BaseScenarioResult.class)))
                    .willThrow(new RuntimeException("JSON parse error"));

            // When
            CompletableFuture<BaseScenarioResult> result = aiService.generateBaseScenario(testBaseLine);

            // Then
            assertThatThrownBy(result::join)
                    .hasCauseInstanceOf(AiParsingException.class)
                    .hasMessageContaining("Failed to parse BaseScenario response");

            verify(textAiClient).generateText(anyString());
            verify(objectMapper).readValue(eq(invalidJsonResponse), eq(BaseScenarioResult.class));
        }
    }

    @Nested
    @DisplayName("결정 시나리오 생성")
    class GenerateDecisionScenario {

        @Test
        @DisplayName("성공 - 정상적인 DecisionLine과 BaseScenario로 결정 시나리오 생성")
        void generateDecisionScenario_성공() throws Exception {
            // Given
            String mockAiResponse = """
                {
                    "job": "대학 교수",
                    "summary": "학문 연구에 집중하는 삶",
                    "description": "대학원 진학 후 박사 과정을 거쳐 교수가 되어 연구에 매진하고 있습니다.",
                    "total": 280,
                    "imagePrompt": "professor teaching in university classroom",
                    "indicators": [
                        {"type": "경제", "point": 45, "analysis": "안정적이지만 높지 않은 소득"},
                        {"type": "행복", "point": 65, "analysis": "학문적 성취로 높은 만족도"},
                        {"type": "관계", "point": 50, "analysis": "학계 중심의 인간관계"},
                        {"type": "직업", "point": 70, "analysis": "전문성 높은 직업"},
                        {"type": "건강", "point": 50, "analysis": "연구 스트레스로 보통 수준"}
                    ],
                    "timelineTitles": {
                        "2018": "대학원 진학",
                        "2020": "박사과정 진학"
                    },
                    "comparisons": [
                        {"type": "TOTAL", "baseScore": 250, "newScore": 280, "analysis": "전반적 향상"}
                    ]
                }
                """;

            // Record 생성자 사용 (builder 대신)
            DecisionScenarioResult expectedResult = new DecisionScenarioResult(
                    "대학 교수",                                                     // job
                    "학문 연구에 집중하는 삶",                                           // summary
                    "대학원 진학 후 박사 과정을 거쳐 교수가 되어 연구에 매진하고 있습니다.",      // description
                    280,                                                       // total
                    "professor teaching in university classroom",               // imagePrompt
                    Map.of("2018", "대학원 진학", "2020", "박사과정 진학"),              // timelineTitles
                    Map.of(Type.경제, 45, Type.행복, 65),                          // indicatorScores
                    Map.of(Type.경제, "안정적이지만 높지 않은 소득", Type.행복, "학문적 성취"),  // indicatorAnalysis
                    Map.of("TOTAL", "전반적 향상")                                  // comparisonResults
            );

            // Mock SceneType 설정
            List<SceneType> mockSceneTypes = List.of(
                    new SceneType(testScenario, Type.경제, 50, "평균적 소득"),
                    new SceneType(testScenario, Type.행복, 50, "보통 행복"),
                    new SceneType(testScenario, Type.관계, 50, "적당한 관계"),
                    new SceneType(testScenario, Type.직업, 50, "안정적 직업"),
                    new SceneType(testScenario, Type.건강, 50, "보통 건강")
            );

            given(sceneTypeRepository.findByScenarioIdOrderByTypeAsc(testScenario.getId()))
                    .willReturn(mockSceneTypes);
            given(textAiClient.generateText(anyString()))
                    .willReturn(CompletableFuture.completedFuture(mockAiResponse));
            given(objectMapper.readValue(eq(mockAiResponse), eq(DecisionScenarioResult.class)))
                    .willReturn(expectedResult);

            // When
            CompletableFuture<DecisionScenarioResult> result = aiService.generateDecisionScenario(testDecisionLine, testScenario);

            // Then
            assertThat(result).isCompleted();
            DecisionScenarioResult actualResult = result.join();
            assertThat(actualResult.job()).isEqualTo("대학 교수");
            assertThat(actualResult.summary()).isEqualTo("학문 연구에 집중하는 삶");
            assertThat(actualResult.total()).isEqualTo(280);
            assertThat(actualResult.imagePrompt()).isEqualTo("professor teaching in university classroom");

            verify(sceneTypeRepository).findByScenarioIdOrderByTypeAsc(testScenario.getId());
            verify(textAiClient).generateText(anyString());
            verify(objectMapper).readValue(eq(mockAiResponse), eq(DecisionScenarioResult.class));
        }

        @Test
        @DisplayName("실패 - DecisionLine이 null인 경우")
        void generateDecisionScenario_실패_DecisionLine널() {
            // When
            CompletableFuture<DecisionScenarioResult> result = aiService.generateDecisionScenario(null, testScenario);

            // Then
            assertThatThrownBy(result::join)
                    .hasCauseInstanceOf(AiParsingException.class)
                    .hasMessageContaining("DecisionLine cannot be null");

            verifyNoInteractions(textAiClient);
        }

        @Test
        @DisplayName("실패 - BaseScenario가 null인 경우")
        void generateDecisionScenario_실패_BaseScenario널() {
            // When
            CompletableFuture<DecisionScenarioResult> result = aiService.generateDecisionScenario(testDecisionLine, null);

            // Then
            assertThatThrownBy(result::join)
                    .hasCauseInstanceOf(AiParsingException.class)
                    .hasMessageContaining("BaseScenario cannot be null");

            verifyNoInteractions(textAiClient);
        }
    }

    @Nested
    @DisplayName("상황 생성")
    class GenerateSituation {

        @Test
        @DisplayName("성공 - 정상적인 DecisionNode 목록으로 상황 생성")
        void generateSituation_성공() throws Exception {
            // Given
            List<DecisionNode> previousNodes = testDecisionLine.getDecisionNodes();
            String mockAiResponse = """
                {
                    "situation": "대학원 진학 후 2년이 지나, 연구 성과에 대한 압박이 커지고 있습니다. 지도교수는 박사과정 진학을 강력히 권유하고 있지만, 경제적 부담과 미래에 대한 불안감도 함께 느끼고 있습니다.",
                    "recommendedOption": "박사과정 진학 vs 석사 졸업 후 취업"
                }
                """;

            given(textAiClient.generateText(anyString()))
                    .willReturn(CompletableFuture.completedFuture(mockAiResponse));

            // When
            CompletableFuture<String> result = aiService.generateSituation(previousNodes);

            // Then
            assertThat(result).isCompleted();
            String actualSituation = result.join();
            assertThat(actualSituation).contains("대학원 진학 후 2년이 지나");
            assertThat(actualSituation).contains("연구 성과에 대한 압박");

            verify(textAiClient).generateText(anyString());
        }

        @Test
        @DisplayName("실패 - 이전 노드가 null인 경우")
        void generateSituation_실패_이전노드널() {
            // When
            CompletableFuture<String> result = aiService.generateSituation(null);

            // Then
            assertThatThrownBy(result::join)
                    .hasCauseInstanceOf(AiParsingException.class)
                    .hasMessageContaining("Previous nodes cannot be null or empty");

            verifyNoInteractions(textAiClient);
        }

        @Test
        @DisplayName("실패 - 이전 노드가 빈 목록인 경우")
        void generateSituation_실패_이전노드빈목록() {
            // When
            CompletableFuture<String> result = aiService.generateSituation(List.of());

            // Then
            assertThatThrownBy(result::join)
                    .hasCauseInstanceOf(AiParsingException.class)
                    .hasMessageContaining("Previous nodes cannot be null or empty");

            verifyNoInteractions(textAiClient);
        }
    }

    @Nested
    @DisplayName("예외 처리")
    class ExceptionHandling {

        @Test
        @DisplayName("AI 클라이언트 호출 실패 시 예외 전파")
        void AI클라이언트_호출실패_예외전파() {
            // Given
            RuntimeException clientException = new RuntimeException("AI service unavailable");
            given(textAiClient.generateText(anyString()))
                    .willReturn(CompletableFuture.failedFuture(clientException));

            // When
            CompletableFuture<BaseScenarioResult> result = aiService.generateBaseScenario(testBaseLine);

            // Then
            assertThatThrownBy(result::join)
                    .hasCause(clientException);

            verify(textAiClient).generateText(anyString());
            verifyNoInteractions(objectMapper);
        }

        @Test
        @DisplayName("ObjectMapper 파싱 실패 시 AiParsingException 발생")
        void ObjectMapper_파싱실패_AiParsingException() throws Exception {
            // Given
            String validResponse = "valid json response";
            Exception parseException = new RuntimeException("Invalid JSON format");

            given(textAiClient.generateText(anyString()))
                    .willReturn(CompletableFuture.completedFuture(validResponse));
            given(objectMapper.readValue(eq(validResponse), eq(BaseScenarioResult.class)))
                    .willThrow(parseException);

            // When
            CompletableFuture<BaseScenarioResult> result = aiService.generateBaseScenario(testBaseLine);

            // Then
            assertThatThrownBy(result::join)
                    .hasCauseInstanceOf(AiParsingException.class)
                    .hasMessageContaining("Failed to parse BaseScenario response");

            verify(textAiClient).generateText(anyString());
            verify(objectMapper).readValue(eq(validResponse), eq(BaseScenarioResult.class));
        }
    }
}