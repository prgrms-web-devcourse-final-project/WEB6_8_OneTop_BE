package com.back.global.ai.service;

import com.back.domain.node.entity.BaseLine;
import com.back.domain.node.entity.BaseNode;
import com.back.domain.node.entity.DecisionLine;
import com.back.domain.node.entity.DecisionNode;
import com.back.domain.scenario.entity.Scenario;
import com.back.domain.scenario.entity.SceneType;
import com.back.domain.scenario.entity.Type;
import com.back.domain.scenario.repository.SceneTypeRepository;
import com.back.domain.user.entity.User;
import com.back.global.ai.client.text.TextAiClient;
import com.back.global.ai.config.BaseScenarioAiProperties;
import com.back.global.ai.config.DecisionScenarioAiProperties;
import com.back.global.ai.config.SituationAiProperties;
import com.back.global.ai.dto.AiRequest;
import com.back.global.ai.dto.result.BaseScenarioResult;
import com.back.global.ai.dto.result.DecisionScenarioResult;
import com.back.global.ai.exception.AiParsingException;
import com.back.global.baseentity.BaseEntity;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

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

    @Mock
    private SituationAiProperties situationAiProperties;

    @Mock
    private BaseScenarioAiProperties baseScenarioAiProperties;

    @Mock
    private DecisionScenarioAiProperties decisionScenarioAiProperties;

    @InjectMocks
    private AiServiceImpl aiService;

    private User testUser;
    private BaseLine testBaseLine;
    private DecisionLine testDecisionLine;
    private Scenario testScenario;

    @BeforeEach
    void setUp() throws Exception {
        // 테스트 사용자 생성
        testUser = User.builder()
                .birthdayAt(LocalDateTime.of(2000, 1, 1, 0, 0))
                .build();
        setId(testUser, 1L);

        // 테스트 베이스라인 생성
        testBaseLine = BaseLine.builder()
                .user(testUser)
                .title("현재 삶의 베이스라인")
                .baseNodes(new ArrayList<>())
                .build();
        setId(testBaseLine, 1L);

        // 테스트 베이스 노드 추가
        BaseNode baseNode1 = BaseNode.builder()
                .baseLine(testBaseLine)
                .ageYear(20)
                .situation("대학교 입학")
                .decision("컴퓨터공학과 선택")
                .build();

        BaseNode baseNode2 = BaseNode.builder()
                .baseLine(testBaseLine)
                .ageYear(22)
                .situation("진로 고민")
                .decision("대학원 진학 준비")
                .build();

        testBaseLine.getBaseNodes().add(baseNode1);
        testBaseLine.getBaseNodes().add(baseNode2);

        // 테스트 결정 라인 생성
        testDecisionLine = DecisionLine.builder()
                .user(testUser)
                .baseLine(testBaseLine)
                .decisionNodes(new ArrayList<>())
                .build();
        setId(testDecisionLine, 1L);

        // 테스트 결정 노드 추가
        DecisionNode decisionNode1 = DecisionNode.builder()
                .decisionLine(testDecisionLine)
                .user(testUser)
                .ageYear(23)
                .situation("대학원 진학 1년차, 연구 압박")
                .decision("연구실 변경")
                .build();

        DecisionNode decisionNode2 = DecisionNode.builder()
                .decisionLine(testDecisionLine)
                .user(testUser)
                .ageYear(24)
                .situation("새 연구실에서 학회 발표 기회")
                .decision("해외 학회 참석")
                .build();

        testDecisionLine.getDecisionNodes().add(decisionNode1);
        testDecisionLine.getDecisionNodes().add(decisionNode2);

        // 테스트 시나리오 생성
        testScenario = Scenario.builder()
                .user(testUser)
                .job("소프트웨어 엔지니어")
                .total(250)
                .summary("안정적인 IT 직장 생활")
                .description("대학원을 졸업하고 중견 IT 기업에 취직하여...")
                .build();
        setId(testScenario, 1L);
    }

    private void setId(Object entity, Long id) throws Exception {
        var idField = BaseEntity.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(entity, id);
    }

    @Nested
    @DisplayName("베이스 시나리오 생성 테스트")
    class GenerateBaseScenarioTests {

        @Test
        @DisplayName("성공 - 유효한 BaseLine으로 베이스 시나리오 생성")
        void generateBaseScenario_Success() throws Exception {
            // given
            String mockAiResponse = """
                {
                    "job": "중견기업 개발자",
                    "summary": "안정적인 직장 생활",
                    "description": "대학원을 졸업하고 IT 기업에 취직",
                    "total": 250,
                    "timelineTitles": {"2022": "대학원 입학", "2024": "졸업"},
                    "baselineTitle": "현재 삶 유지",
                    "indicatorScores": {"경제": 50, "행복": 50, "관계": 50, "직업": 50, "건강": 50},
                    "indicatorAnalysis": {"경제": "평균", "행복": "평균", "관계": "평균", "직업": "평균", "건강": "평균"}
                }
                """;

            BaseScenarioResult expectedResult = new BaseScenarioResult(
                    "중견기업 개발자",
                    "안정적인 직장 생활",
                    "대학원을 졸업하고 IT 기업에 취직",
                    250,
                    Map.of("2022", "대학원 입학", "2024", "졸업"),
                    "현재 삶 유지",
                    Map.of(Type.경제, 50, Type.행복, 50, Type.관계, 50, Type.직업, 50, Type.건강, 50),
                    Map.of(Type.경제, "평균", Type.행복, "평균", Type.관계, "평균", Type.직업, "평균", Type.건강, "평균")
            );

            given(baseScenarioAiProperties.getMaxOutputTokens()).willReturn(1500);
            given(textAiClient.generateText(any(AiRequest.class)))
                    .willReturn(CompletableFuture.completedFuture(mockAiResponse));
            given(objectMapper.readValue(mockAiResponse, BaseScenarioResult.class))
                    .willReturn(expectedResult);

            // when
            CompletableFuture<BaseScenarioResult> future = aiService.generateBaseScenario(testBaseLine);
            BaseScenarioResult result = future.get();

            // then
            assertThat(result).isNotNull();
            assertThat(result.job()).isEqualTo("중견기업 개발자");
            assertThat(result.total()).isEqualTo(250);
            assertThat(result.summary()).isEqualTo("안정적인 직장 생활");

            verify(textAiClient, times(1)).generateText(any(AiRequest.class));
            verify(objectMapper, times(1)).readValue(mockAiResponse, BaseScenarioResult.class);
        }

        @Test
        @DisplayName("실패 - BaseLine이 null인 경우")
        void generateBaseScenario_Fail_NullBaseLine() {
            // when & then
            assertThatThrownBy(() -> aiService.generateBaseScenario(null).get())
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(AiParsingException.class)
                    .hasMessageContaining("BaseLine cannot be null");

            verify(textAiClient, never()).generateText(any(AiRequest.class));
        }

        @Test
        @DisplayName("실패 - AI 응답 파싱 실패")
        void generateBaseScenario_Fail_ParsingError() throws Exception {
            // given
            String invalidAiResponse = "Invalid JSON response";

            given(baseScenarioAiProperties.getMaxOutputTokens()).willReturn(1500);
            given(textAiClient.generateText(any(AiRequest.class)))
                    .willReturn(CompletableFuture.completedFuture(invalidAiResponse));
            given(objectMapper.readValue(invalidAiResponse, BaseScenarioResult.class))
                    .willThrow(new RuntimeException("JSON parse error"));

            // when & then
            assertThatThrownBy(() -> aiService.generateBaseScenario(testBaseLine).get())
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(AiParsingException.class)
                    .hasMessageContaining("Failed to parse BaseScenario response");

            verify(textAiClient, times(1)).generateText(any(AiRequest.class));
        }
    }

    @Nested
    @DisplayName("결정 시나리오 생성 테스트")
    class GenerateDecisionScenarioTests {

        @Test
        @DisplayName("성공 - 유효한 DecisionLine과 BaseScenario로 시나리오 생성")
        void generateDecisionScenario_Success() throws Exception {
            // given
            String mockAiResponse = """
                {
                    "job": "스타트업 CTO",
                    "summary": "도전적인 창업 생활",
                    "description": "연구실 경험을 바탕으로 AI 스타트업 창업",
                    "total": 300,
                    "imagePrompt": "Young CTO working in startup office",
                    "timelineTitles": {"2023": "연구실 변경", "2024": "해외 학회"},
                    "indicatorScores": {"경제": 60, "행복": 65, "관계": 55, "직업": 70, "건강": 50},
                    "indicatorAnalysis": {"경제": "향상", "행복": "향상", "관계": "유지", "직업": "향상", "건강": "유지"},
                    "comparisonResults": {"TOTAL": "전체적으로 향상", "경제": "10점 상승", "행복": "15점 상승"}
                }
                """;

            DecisionScenarioResult expectedResult = new DecisionScenarioResult(
                    "스타트업 CTO",
                    "도전적인 창업 생활",
                    "연구실 경험을 바탕으로 AI 스타트업 창업",
                    300,
                    "Young CTO working in startup office",
                    Map.of("2023", "연구실 변경", "2024", "해외 학회"),
                    Map.of(Type.경제, 60, Type.행복, 65, Type.관계, 55, Type.직업, 70, Type.건강, 50),
                    Map.of(Type.경제, "향상", Type.행복, "향상", Type.관계, "유지", Type.직업, "향상", Type.건강, "유지"),
                    Map.of("TOTAL", "전체적으로 향상", "경제", "10점 상승", "행복", "15점 상승")
            );

            List<SceneType> baseSceneTypes = createMockSceneTypes();

            given(decisionScenarioAiProperties.getMaxOutputTokens()).willReturn(2000);
            given(sceneTypeRepository.findByScenarioIdOrderByTypeAsc(testScenario.getId()))
                    .willReturn(baseSceneTypes);
            given(textAiClient.generateText(any(AiRequest.class)))
                    .willReturn(CompletableFuture.completedFuture(mockAiResponse));
            given(objectMapper.readValue(mockAiResponse, DecisionScenarioResult.class))
                    .willReturn(expectedResult);

            // when
            CompletableFuture<DecisionScenarioResult> future =
                    aiService.generateDecisionScenario(testDecisionLine, testScenario);
            DecisionScenarioResult result = future.get();

            // then
            assertThat(result).isNotNull();
            assertThat(result.job()).isEqualTo("스타트업 CTO");
            assertThat(result.total()).isEqualTo(300);
            assertThat(result.summary()).isEqualTo("도전적인 창업 생활");
            assertThat(result.imagePrompt()).isEqualTo("Young CTO working in startup office");

            verify(textAiClient, times(1)).generateText(any(AiRequest.class));
            verify(sceneTypeRepository, times(1)).findByScenarioIdOrderByTypeAsc(testScenario.getId());
        }

        @Test
        @DisplayName("실패 - DecisionLine이 null인 경우")
        void generateDecisionScenario_Fail_NullDecisionLine() {
            // when & then
            assertThatThrownBy(() -> aiService.generateDecisionScenario(null, testScenario).get())
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(AiParsingException.class)
                    .hasMessageContaining("DecisionLine cannot be null");

            verify(textAiClient, never()).generateText(any(AiRequest.class));
        }

        @Test
        @DisplayName("실패 - BaseScenario가 null인 경우")
        void generateDecisionScenario_Fail_NullBaseScenario() {
            // when & then
            assertThatThrownBy(() -> aiService.generateDecisionScenario(testDecisionLine, null).get())
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(AiParsingException.class)
                    .hasMessageContaining("BaseScenario cannot be null");

            verify(textAiClient, never()).generateText(any(AiRequest.class));
        }

        @Test
        @DisplayName("성공 - SceneType이 비어있어도 정상 처리")
        void generateDecisionScenario_Success_EmptySceneTypes() throws Exception {
            // given
            String mockAiResponse = """
                {
                    "job": "스타트업 CTO",
                    "summary": "도전적인 창업 생활",
                    "description": "연구실 경험을 바탕으로 AI 스타트업 창업",
                    "total": 300,
                    "imagePrompt": "Young CTO working in startup office",
                    "timelineTitles": {"2023": "연구실 변경"},
                    "indicatorScores": {"경제": 60},
                    "indicatorAnalysis": {"경제": "향상"},
                    "comparisonResults": {"TOTAL": "전체적으로 향상"}
                }
                """;

            DecisionScenarioResult expectedResult = new DecisionScenarioResult(
                    "스타트업 CTO",
                    "도전적인 창업 생활",
                    "연구실 경험을 바탕으로 AI 스타트업 창업",
                    300,
                    "Young CTO working in startup office",
                    Map.of("2023", "연구실 변경"),
                    Map.of(Type.경제, 60),
                    Map.of(Type.경제, "향상"),
                    Map.of("TOTAL", "전체적으로 향상")
            );

            given(decisionScenarioAiProperties.getMaxOutputTokens()).willReturn(2000);
            given(sceneTypeRepository.findByScenarioIdOrderByTypeAsc(testScenario.getId()))
                    .willReturn(List.of()); // 빈 리스트 반환
            given(textAiClient.generateText(any(AiRequest.class)))
                    .willReturn(CompletableFuture.completedFuture(mockAiResponse));
            given(objectMapper.readValue(mockAiResponse, DecisionScenarioResult.class))
                    .willReturn(expectedResult);

            // when
            CompletableFuture<DecisionScenarioResult> future =
                    aiService.generateDecisionScenario(testDecisionLine, testScenario);
            DecisionScenarioResult result = future.get();

            // then
            assertThat(result).isNotNull();
            assertThat(result.job()).isEqualTo("스타트업 CTO");

            verify(sceneTypeRepository, times(1)).findByScenarioIdOrderByTypeAsc(testScenario.getId());
        }
    }

    @Nested
    @DisplayName("상황 생성 테스트")
    class GenerateSituationTests {

        @Test
        @DisplayName("성공 - 유효한 이전 선택들로 새로운 상황 생성")
        void generateSituation_Success() throws Exception {
            // given
            List<DecisionNode> previousNodes = testDecisionLine.getDecisionNodes();
            String mockAiResponse = """
                {
                    "situation": "해외 학회 참석 6개월 후, 글로벌 기업에서 채용 제안을 받았습니다.",
                    "recommendedOption": "제안 수락"
                }
                """;

            given(situationAiProperties.getMaxOutputTokens()).willReturn(384);
            given(textAiClient.generateText(any(AiRequest.class)))
                    .willReturn(CompletableFuture.completedFuture(mockAiResponse));

            // when
            CompletableFuture<String> future = aiService.generateSituation(previousNodes);
            String result = future.get();

            // then
            assertThat(result).isNotNull();
            assertThat(result).isEqualTo("해외 학회 참석 6개월 후, 글로벌 기업에서 채용 제안을 받았습니다.");

            verify(textAiClient, times(1)).generateText(any(AiRequest.class));
        }

        @Test
        @DisplayName("실패 - previousNodes가 null인 경우")
        void generateSituation_Fail_NullPreviousNodes() {
            // when & then
            assertThatThrownBy(() -> aiService.generateSituation(null).get())
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(AiParsingException.class)
                    .hasMessageContaining("Previous nodes cannot be null or empty");

            verify(textAiClient, never()).generateText(any(AiRequest.class));
        }

        @Test
        @DisplayName("실패 - previousNodes가 빈 리스트인 경우")
        void generateSituation_Fail_EmptyPreviousNodes() {
            // when & then
            assertThatThrownBy(() -> aiService.generateSituation(List.of()).get())
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(AiParsingException.class)
                    .hasMessageContaining("Previous nodes cannot be null or empty");

            verify(textAiClient, never()).generateText(any(AiRequest.class));
        }

        @Test
        @DisplayName("실패 - previousNodes에 유효하지 않은 데이터가 있는 경우")
        void generateSituation_Fail_InvalidNodeData() {
            // given
            DecisionNode invalidNode = DecisionNode.builder()
                    .user(testUser)
                    .ageYear(25)
                    .situation(null) // 유효하지 않은 데이터
                    .decision("결정만 있음")
                    .build();

            List<DecisionNode> invalidNodes = List.of(invalidNode);

            // when & then
            assertThatThrownBy(() -> aiService.generateSituation(invalidNodes).get())
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(AiParsingException.class)
                    .hasMessageContaining("Previous nodes contain invalid data");

            verify(textAiClient, never()).generateText(any(AiRequest.class));
        }

        @Test
        @DisplayName("성공 - AI 응답에서 상황 추출 (JSON 형식)")
        void generateSituation_Success_ExtractSituationFromJson() throws Exception {
            // given
            List<DecisionNode> previousNodes = testDecisionLine.getDecisionNodes();
            String mockAiResponse = """
                {
                    "situation": "연구 성과를 인정받아 유명 학술지에 논문이 게재되었습니다.",
                    "recommendedOption": "박사 과정 진학"
                }
                """;

            given(situationAiProperties.getMaxOutputTokens()).willReturn(384);
            given(textAiClient.generateText(any(AiRequest.class)))
                    .willReturn(CompletableFuture.completedFuture(mockAiResponse));

            // when
            CompletableFuture<String> future = aiService.generateSituation(previousNodes);
            String result = future.get();

            // then
            assertThat(result).isNotNull();
            assertThat(result).isEqualTo("연구 성과를 인정받아 유명 학술지에 논문이 게재되었습니다.");

            verify(textAiClient, times(1)).generateText(any(AiRequest.class));
        }
    }

    @Nested
    @DisplayName("이미지 생성 테스트")
    class GenerateImageTests {

        @Test
        @DisplayName("기본 구현 - placeholder 이미지 URL 반환")
        void generateImage_DefaultImplementation() throws Exception {
            // when
            CompletableFuture<String> future = aiService.generateImage("test prompt");
            String result = future.get();

            // then
            assertThat(result).isEqualTo("placeholder-image-url");
        }
    }

    private List<SceneType> createMockSceneTypes() {
        List<SceneType> sceneTypes = new ArrayList<>();

        sceneTypes.add(SceneType.builder()
                .scenario(testScenario)
                .type(Type.경제)
                .point(50)
                .analysis("평균적인 경제 상황")
                .build());

        sceneTypes.add(SceneType.builder()
                .scenario(testScenario)
                .type(Type.행복)
                .point(50)
                .analysis("평균적인 행복도")
                .build());

        sceneTypes.add(SceneType.builder()
                .scenario(testScenario)
                .type(Type.관계)
                .point(50)
                .analysis("평균적인 인간관계")
                .build());

        sceneTypes.add(SceneType.builder()
                .scenario(testScenario)
                .type(Type.직업)
                .point(50)
                .analysis("안정적인 직업")
                .build());

        sceneTypes.add(SceneType.builder()
                .scenario(testScenario)
                .type(Type.건강)
                .point(50)
                .analysis("평균적인 건강 상태")
                .build());

        return sceneTypes;
    }
}
