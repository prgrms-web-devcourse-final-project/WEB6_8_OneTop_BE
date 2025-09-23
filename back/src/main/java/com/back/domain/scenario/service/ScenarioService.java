package com.back.domain.scenario.service;

import com.back.domain.node.repository.BaseLineRepository;
import com.back.domain.node.repository.DecisionLineRepository;
import com.back.domain.scenario.dto.*;
import com.back.domain.scenario.entity.Scenario;
import com.back.domain.scenario.entity.ScenarioStatus;
import com.back.domain.scenario.entity.SceneCompare;
import com.back.domain.scenario.repository.ScenarioRepository;
import com.back.domain.scenario.repository.SceneCompareRepository;
import com.back.domain.scenario.repository.SceneTypeRepository;
import com.back.domain.user.repository.UserRepository;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 시나리오 관련 비즈니스 로직을 처리하는 서비스.
 * 시나리오 생성, 상세 조회, 비교 등의 기능을 제공합니다.
 */
@Service
@RequiredArgsConstructor
public class ScenarioService {

    // Repository 주입
    private final ScenarioRepository scenarioRepository;
    private final SceneTypeRepository sceneTypeRepository;
    private final SceneCompareRepository sceneCompareRepository;

    // Node Repository 주입
    private final DecisionLineRepository decisionLineRepository;
    private final BaseLineRepository baseLineRepository;

    // AI Service 주입 (추후 구현 시 필요, AI 호출용)
    // private final AiService aiService;

    // User Repository 주입 (추후 구현 시 필요, 권한 검증용)
    private final UserRepository userRepository;

    // 시나리오 생성
    @Transactional
    public ScenarioStatusResponse createScenario(Long userId, ScenarioCreateRequest request) {

    }

    // 시나리오 생성 상태 조회
    @Transactional(readOnly = true)
    public ScenarioStatusResponse getScenarioStatus(Long scenarioId, Long userId) {
        // 권한 검증 및 조회
        Scenario scenario = scenarioRepository.findByIdAndUserId(scenarioId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.SCENARIO_NOT_FOUND));

        // DTO 변환 및 반환
        return new ScenarioStatusResponse(
                scenario.getId(),
                scenario.getStatus(),
                getStatusMessage(scenario.getStatus())
        );
    }

    // 시나리오 생성 상태 조회 Helper 메서드 (상태별 메세지)
    private String getStatusMessage(ScenarioStatus status) {
        return switch (status) {
            case PENDING -> "시나리오 생성 대기 중입니다.";
            case PROCESSING -> "시나리오를 생성 중입니다.";
            case COMPLETED -> "시나리오 생성이 완료되었습니다.";
            case FAILED -> "시나리오 생성에 실패했습니다. 다시 시도해주세요.";
        };
    }

    // 시나리오 상세 조회
    @Transactional(readOnly = true)
    public ScenarioDetailResponse getScenarioDetail(Long scenarioId, Long userId) {
        // 권한 검증 및 조회
        Scenario scenario = scenarioRepository.findByIdAndUserId(scenarioId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.SCENARIO_NOT_FOUND));

        // 지표 조회
        var sceneTypes = sceneTypeRepository.findByScenarioIdOrderByTypeAsc(scenarioId);

        // DTO 변환 및 반환
        return new ScenarioDetailResponse(
                scenario.getId(),
                scenario.getStatus(),
                scenario.getJob(),
                scenario.getTotal(),
                scenario.getSummary(),
                scenario.getDescription(),
                scenario.getImg(),
                scenario.getCreatedDate(),
                sceneTypes.stream()
                        .map(st -> new ScenarioTypeDto(st.getType(), st.getPoint(), st.getAnalysis()))
                        .toList()
        );
    }

    // 시나리오 타임라인 조회
    @Transactional(readOnly = true)
    public TimelineResponse getScenarioTimeline(Long scenarioId, Long userId) {
        // 권한 검증 및 시나리오 조회
        Scenario scenario = scenarioRepository.findByIdAndUserId(scenarioId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.SCENARIO_NOT_FOUND));

        // DecisionLine 조회 -> DecisionNodes 추출 (DecisionLine 미구현으로 임시)
        // TODO: DecisionLine 및 DecisionNode 구현 후 교체
        List<MockDecisionNode> mockNodes = createMockDecisionNodes();

        // TimelineTitles JSON 파싱
        Map<String, String> timelineTitles = parseTimelineTitles(scenario.getTimelineTitles());

        // TimelineEvent 리스트 생성
        List<TimelineResponse.TimelineEvent> events = mockNodes.stream()
                .map(node -> new TimelineResponse.TimelineEvent(
                        node.year,
                        timelineTitles.getOrDefault(String.valueOf(node.year), "선택 결과")
                ))
                .sorted(Comparator.comparing(TimelineResponse.TimelineEvent::year))
                .toList();

        // DTO 변환 및 반환
        return new TimelineResponse(scenarioId, events);
    }

    // 시나리오 타임라인 조회 Helper
    // Mock DecisionNode 클래스 (추후 실제 엔티티로 교체)
    private record MockDecisionNode(int year, String title) {}

    // Mock 데이터 생성 메서드 (추후 실제 데이터로 교체)
    private List<MockDecisionNode> createMockDecisionNodes() {
        return List.of(
                new MockDecisionNode(2020, "창업 도전"),
                new MockDecisionNode(2022, "해외 진출"),
                new MockDecisionNode(2025, "상장 성공")
        );
    }

    // JSON 파싱 메서드 (추후 구현)
    private Map<String, String> parseTimelineTitles(String timelineTitles) {
        // TODO: 실제 JSON 파싱 구현 (ObjectMapper 사용)
        // 현재는 Mock 데이터 반환
        return Map.of(
                "2020", "창업 도전",
                "2022", "해외 진출",
                "2025", "상장 성공"
        );
    }

    // 시나리오 비교 분석
    @Transactional(readOnly = true)
    public ScenarioCompareResponse compareScenarios(Long baseId, Long compareId, Long userId) {
        // 권한 검증 및 시나리오 조회
        Scenario baseScenario = scenarioRepository.findByIdAndUserId(baseId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.SCENARIO_NOT_FOUND));
        Scenario compareScenario = scenarioRepository.findByIdAndUserId(compareId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.SCENARIO_NOT_FOUND));

        // 지표 조회
        var baseTypes = sceneTypeRepository.findByScenarioIdOrderByTypeAsc(baseId);
        var compareTypes = sceneTypeRepository.findByScenarioIdOrderByTypeAsc(compareId);

        // 비교 분석 결과 조회
        List<SceneCompare> compareResults = sceneCompareRepository.findByScenarioIdOrderByResultType(compareId);
        if (compareResults.isEmpty()) {
            throw new ApiException(ErrorCode.SCENE_COMPARE_NOT_FOUND);
        }

        // DTO 변환 및 반환
        return ScenarioCompareResponse.from(
                baseScenario,
                compareScenario,
                compareResults,
                baseTypes,
                compareTypes
        );
    }

    // 베이스라인 목록 조회
    @Transactional(readOnly = true)
    public List<BaselineListResponse> getBaselines(Long userId) {
        // TODO: 실제 구현 시 BaseLineRepository.findAllByUserId(userId) 사용
        // 현재는 Mock 데이터로 MVP 완성

        // Mock 베이스라인 데이터 생성
        return List.of(
                new BaselineListResponse(
                        1001L,
                        "대학 졸업 후 진로 선택",
                        List.of("교육", "진로", "취업"),
                        LocalDateTime.of(2024, 1, 15, 10, 30)
                ),
                new BaselineListResponse(
                        1002L,
                        "회사 이직 후 새 시작",
                        List.of("커리어", "성장", "도전"),
                        LocalDateTime.of(2024, 3, 22, 14, 45)
                ),
                new BaselineListResponse(
                        1003L,
                        "결혼 후 인생 설계",
                        List.of("가족", "관계", "안정"),
                        LocalDateTime.of(2024, 6, 10, 16, 20)
                )
        );
}