package com.back.domain.scenario.service;

import com.back.domain.node.entity.BaseLine;
import com.back.domain.node.entity.DecisionLine;
import com.back.domain.node.repository.BaseLineRepository;
import com.back.domain.node.repository.DecisionLineRepository;
import com.back.domain.scenario.dto.ScenarioCreateRequest;
import com.back.domain.scenario.dto.ScenarioStatusResponse;
import com.back.domain.scenario.entity.Scenario;
import com.back.domain.scenario.entity.ScenarioStatus;
import com.back.domain.scenario.repository.ScenarioRepository;
import com.back.domain.scenario.repository.SceneCompareRepository;
import com.back.domain.scenario.repository.SceneTypeRepository;
import com.back.domain.user.entity.User;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;

/**
 * ScenarioService 단위 테스트.
 * 비즈니스 로직의 핵심 기능들을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ScenarioService 단위 테스트")
class ScenarioServiceTest {

    @Mock private ScenarioRepository scenarioRepository;
    @Mock private SceneTypeRepository sceneTypeRepository;
    @Mock private SceneCompareRepository sceneCompareRepository;
    @Mock private DecisionLineRepository decisionLineRepository;
    @Mock private BaseLineRepository baseLineRepository;
    @Mock private ObjectMapper objectMapper;

    @Spy @InjectMocks private ScenarioService scenarioService;

    @Nested
    @DisplayName("시나리오 생성")
    class CreateScenarioTests {

        @Test
        @DisplayName("성공 - 시나리오 생성 요청 접수")
        void createScenario_성공_시나리오생성요청접수() {
            // Given
            Long userId = 1L;
            Long decisionLineId = 100L;
            ScenarioCreateRequest request = new ScenarioCreateRequest(decisionLineId);

            User mockUser = User.builder().build();
            ReflectionTestUtils.setField(mockUser, "id", userId);

            BaseLine mockBaseLine = BaseLine.builder().user(mockUser).build();
            ReflectionTestUtils.setField(mockBaseLine, "id", 200L);

            DecisionLine mockDecisionLine = DecisionLine.builder()
                    .user(mockUser)
                    .baseLine(mockBaseLine)
                    .build();
            ReflectionTestUtils.setField(mockDecisionLine, "id", decisionLineId);

            Scenario savedScenario = Scenario.builder()
                    .user(mockUser)
                    .decisionLine(mockDecisionLine)
                    .status(ScenarioStatus.PENDING)
                    .build();
            ReflectionTestUtils.setField(savedScenario, "id", 1001L);

            // 중요: 비동기 메서드에서 호출되는 repository.findById()도 모킹 필요
            given(decisionLineRepository.findById(decisionLineId))
                    .willReturn(Optional.of(mockDecisionLine));
            given(scenarioRepository.existsByDecisionLineIdAndStatus(decisionLineId, ScenarioStatus.PENDING))
                    .willReturn(false);
            given(scenarioRepository.existsByDecisionLineIdAndStatus(decisionLineId, ScenarioStatus.PROCESSING))
                    .willReturn(false);
            given(scenarioRepository.existsByDecisionLine_BaseLineId(200L))
                    .willReturn(true); // 베이스 시나리오 이미 존재
            given(scenarioRepository.save(any(Scenario.class)))
                    .willReturn(savedScenario);

            // 비동기 메서드 호출을 무효화 (동기 로직만 테스트)
            doNothing().when(scenarioService).processScenarioGenerationAsync(anyLong());

            // When
            ScenarioStatusResponse result = scenarioService.createScenario(userId, request);

            // Then - 시나리오 생성 요청이 접수되고 PENDING 상태로 반환되는지만 검증
            assertThat(result).isNotNull();
            assertThat(result.scenarioId()).isEqualTo(1001L);
            assertThat(result.status()).isEqualTo(ScenarioStatus.PENDING);
            assertThat(result.message()).isEqualTo("시나리오 생성이 시작되었습니다.");

            // 동기 부분의 핵심 비즈니스 로직만 검증
            verify(decisionLineRepository).findById(decisionLineId);
            verify(scenarioRepository).existsByDecisionLineIdAndStatus(decisionLineId, ScenarioStatus.PENDING);
            verify(scenarioRepository).existsByDecisionLineIdAndStatus(decisionLineId, ScenarioStatus.PROCESSING);
            verify(scenarioRepository).existsByDecisionLine_BaseLineId(200L);
            verify(scenarioRepository).save(any(Scenario.class));

            // 비동기 메서드가 호출되었는지 확인 (하지만 실행은 되지 않음)
            verify(scenarioService).processScenarioGenerationAsync(1001L);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 DecisionLine")
        void createScenario_실패_존재하지않는_DecisionLine() {
            // Given
            Long userId = 1L;
            Long decisionLineId = 999L;
            ScenarioCreateRequest request = new ScenarioCreateRequest(decisionLineId);

            given(decisionLineRepository.findById(decisionLineId))
                    .willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> scenarioService.createScenario(userId, request))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DECISION_LINE_NOT_FOUND);

            verify(decisionLineRepository).findById(decisionLineId);
            verify(scenarioRepository, never()).save(any());
        }

        @Test
        @DisplayName("실패 - 권한이 없는 사용자")
        void createScenario_실패_권한없는_사용자() {
            // Given
            Long userId = 1L;
            Long unauthorizedUserId = 999L;
            Long decisionLineId = 100L;
            ScenarioCreateRequest request = new ScenarioCreateRequest(decisionLineId);

            User unauthorizedUser = User.builder().build();
            ReflectionTestUtils.setField(unauthorizedUser, "id", unauthorizedUserId);

            DecisionLine mockDecisionLine = DecisionLine.builder()
                    .user(unauthorizedUser) // 다른 사용자 소유
                    .build();
            ReflectionTestUtils.setField(mockDecisionLine, "id", decisionLineId);

            given(decisionLineRepository.findById(decisionLineId))
                    .willReturn(Optional.of(mockDecisionLine));

            // When & Then
            assertThatThrownBy(() -> scenarioService.createScenario(userId, request))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.HANDLE_ACCESS_DENIED);

            verify(decisionLineRepository).findById(decisionLineId);
            verify(scenarioRepository, never()).save(any());
        }

        @Test
        @DisplayName("실패 - 이미 진행 중인 시나리오 존재")
        void createScenario_실패_이미_진행중인_시나리오_존재() {
            // Given
            Long userId = 1L;
            Long decisionLineId = 100L;
            ScenarioCreateRequest request = new ScenarioCreateRequest(decisionLineId);

            User mockUser = User.builder().build();
            ReflectionTestUtils.setField(mockUser, "id", userId);

            DecisionLine mockDecisionLine = DecisionLine.builder()
                    .user(mockUser)
                    .build();
            ReflectionTestUtils.setField(mockDecisionLine, "id", decisionLineId);

            given(decisionLineRepository.findById(decisionLineId))
                    .willReturn(Optional.of(mockDecisionLine));
            given(scenarioRepository.existsByDecisionLineIdAndStatus(decisionLineId, ScenarioStatus.PENDING))
                    .willReturn(true); // 이미 PENDING 상태 시나리오 존재

            // When & Then
            assertThatThrownBy(() -> scenarioService.createScenario(userId, request))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SCENARIO_ALREADY_IN_PROGRESS);

            verify(scenarioRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("비동기 시나리오 생성 처리")
    class ProcessScenarioGenerationAsyncTests {

        @Test
        @DisplayName("성공 - 비동기 시나리오 생성 및 완료")
        void processScenarioGenerationAsync_성공_시나리오생성완료() {
            // Given
            Long scenarioId = 1001L;
            Scenario mockScenario = Scenario.builder()
                    .status(ScenarioStatus.PENDING)
                    .build();
            ReflectionTestUtils.setField(mockScenario, "id", scenarioId);

            given(scenarioRepository.findById(scenarioId))
                    .willReturn(Optional.of(mockScenario));
            given(scenarioRepository.save(any(Scenario.class)))
                    .willReturn(mockScenario);

            // When
            scenarioService.processScenarioGenerationAsync(scenarioId);

            // Then
            // 비동기 메서드이므로 호출 여부만 검증
            verify(scenarioRepository, atLeast(1)).findById(scenarioId);
            verify(scenarioRepository, atLeast(2)).save(any(Scenario.class)); // PROCESSING, COMPLETED 상태 저장
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 시나리오 (비동기 처리)")
        void processScenarioGenerationAsync_실패_존재하지않는_시나리오() {
            // Given
            Long scenarioId = 999L;
            given(scenarioRepository.findById(scenarioId))
                    .willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> scenarioService.processScenarioGenerationAsync(scenarioId))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SCENARIO_NOT_FOUND);

            verify(scenarioRepository).findById(scenarioId);
            verify(scenarioRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("시나리오 상태 조회")
    class GetScenarioStatusTests {

        @Test
        @DisplayName("성공 - 유효한 시나리오 상태 조회")
        void getScenarioStatus_성공_유효한_시나리오_상태_조회() {
            // Given
            Long scenarioId = 1001L;
            Long userId = 1L;

            Scenario mockScenario = Scenario.builder()
                    .status(ScenarioStatus.COMPLETED)
                    .build();
            ReflectionTestUtils.setField(mockScenario, "id", scenarioId);

            given(scenarioRepository.findByIdAndUserId(scenarioId, userId))
                    .willReturn(Optional.of(mockScenario));

            // When
            ScenarioStatusResponse result = scenarioService.getScenarioStatus(scenarioId, userId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.scenarioId()).isEqualTo(scenarioId);
            assertThat(result.status()).isEqualTo(ScenarioStatus.COMPLETED);
            assertThat(result.message()).isEqualTo("시나리오 생성이 완료되었습니다.");

            verify(scenarioRepository).findByIdAndUserId(scenarioId, userId);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 시나리오")
        void getScenarioStatus_실패_존재하지않는_시나리오() {
            // Given
            Long scenarioId = 999L;
            Long userId = 1L;

            given(scenarioRepository.findByIdAndUserId(scenarioId, userId))
                    .willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> scenarioService.getScenarioStatus(scenarioId, userId))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SCENARIO_NOT_FOUND);

            verify(scenarioRepository).findByIdAndUserId(scenarioId, userId);
        }
    }

    @Nested
    @DisplayName("JSON 파싱 로직")
    class ParseTimelineTitlesTests {

        @Test
        @DisplayName("성공 - 유효한 JSON 파싱")
        void parseTimelineTitles_성공_유효한_JSON_파싱() throws Exception {
            // Given
            String validJson = "{\"2020\":\"창업 시작\",\"2025\":\"상장 성공\"}";
            Map<String, String> expectedResult = Map.of("2020", "창업 시작", "2025", "상장 성공");

            given(objectMapper.readValue(eq(validJson), any(TypeReference.class)))
                    .willReturn(expectedResult);

            // When
            // parseTimelineTitles는 private이므로 reflection으로 테스트하거나
            // public 메서드를 통해 간접적으로 테스트
            // 여기서는 getScenarioTimeline을 통해 간접 테스트
            Long scenarioId = 1001L;
            Long userId = 1L;

            Scenario mockScenario = Scenario.builder()
                    .timelineTitles(validJson)
                    .build();
            ReflectionTestUtils.setField(mockScenario, "id", scenarioId);

            given(scenarioRepository.findByIdAndUserId(scenarioId, userId))
                    .willReturn(Optional.of(mockScenario));

            // Then
            // parseTimelineTitles가 정상 동작하면 예외가 발생하지 않아야 함
            assertThatCode(() -> scenarioService.getScenarioTimeline(scenarioId, userId))
                    .doesNotThrowAnyException();

            verify(objectMapper).readValue(eq(validJson), any(TypeReference.class));
        }

        @Test
        @DisplayName("실패 - null 입력값")
        void parseTimelineTitles_실패_null_입력값() {
            // Given
            Long scenarioId = 1001L;
            Long userId = 1L;

            Scenario mockScenario = Scenario.builder()
                    .timelineTitles(null)
                    .build();
            ReflectionTestUtils.setField(mockScenario, "id", scenarioId);

            given(scenarioRepository.findByIdAndUserId(scenarioId, userId))
                    .willReturn(Optional.of(mockScenario));

            // When & Then
            assertThatThrownBy(() -> scenarioService.getScenarioTimeline(scenarioId, userId))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SCENARIO_TIMELINE_NOT_FOUND);
        }

        @Test
        @DisplayName("실패 - 빈 문자열 입력값")
        void parseTimelineTitles_실패_빈_문자열_입력값() {
            // Given
            Long scenarioId = 1001L;
            Long userId = 1L;

            Scenario mockScenario = Scenario.builder()
                    .timelineTitles("   ") // 공백만 있는 문자열
                    .build();
            ReflectionTestUtils.setField(mockScenario, "id", scenarioId);

            given(scenarioRepository.findByIdAndUserId(scenarioId, userId))
                    .willReturn(Optional.of(mockScenario));

            // When & Then
            assertThatThrownBy(() -> scenarioService.getScenarioTimeline(scenarioId, userId))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SCENARIO_TIMELINE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("상태 메시지 로직")
    class GetStatusMessageTests {

        @Test
        @DisplayName("성공 - 모든 상태별 메시지 확인")
        void getStatusMessage_성공_모든_상태별_메시지_확인() {
            // Given & When & Then - PENDING
            Long scenarioId = 1001L;
            Long userId = 1L;

            Scenario pendingScenario = Scenario.builder()
                    .status(ScenarioStatus.PENDING)
                    .build();
            ReflectionTestUtils.setField(pendingScenario, "id", scenarioId);
            given(scenarioRepository.findByIdAndUserId(scenarioId, userId))
                    .willReturn(Optional.of(pendingScenario));

            ScenarioStatusResponse pendingResult = scenarioService.getScenarioStatus(scenarioId, userId);
            assertThat(pendingResult.message()).isEqualTo("시나리오 생성 대기 중입니다.");

            // PROCESSING
            Scenario processingScenario = Scenario.builder()
                    .status(ScenarioStatus.PROCESSING)
                    .build();
            ReflectionTestUtils.setField(processingScenario, "id", scenarioId);
            given(scenarioRepository.findByIdAndUserId(scenarioId, userId))
                    .willReturn(Optional.of(processingScenario));

            ScenarioStatusResponse processingResult = scenarioService.getScenarioStatus(scenarioId, userId);
            assertThat(processingResult.message()).isEqualTo("시나리오를 생성 중입니다.");

            // COMPLETED
            Scenario completedScenario = Scenario.builder()
                    .status(ScenarioStatus.COMPLETED)
                    .build();
            ReflectionTestUtils.setField(completedScenario, "id", scenarioId);
            given(scenarioRepository.findByIdAndUserId(scenarioId, userId))
                    .willReturn(Optional.of(completedScenario));

            ScenarioStatusResponse completedResult = scenarioService.getScenarioStatus(scenarioId, userId);
            assertThat(completedResult.message()).isEqualTo("시나리오 생성이 완료되었습니다.");

            // FAILED
            Scenario failedScenario = Scenario.builder()
                    .status(ScenarioStatus.FAILED)
                    .build();
            ReflectionTestUtils.setField(failedScenario, "id", scenarioId);
            given(scenarioRepository.findByIdAndUserId(scenarioId, userId))
                    .willReturn(Optional.of(failedScenario));

            ScenarioStatusResponse failedResult = scenarioService.getScenarioStatus(scenarioId, userId);
            assertThat(failedResult.message()).isEqualTo("시나리오 생성에 실패했습니다. 다시 시도해주세요.");
        }
    }
}