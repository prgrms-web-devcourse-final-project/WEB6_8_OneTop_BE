package com.back.domain.scenario.service;

import com.back.domain.node.entity.BaseLine;
import com.back.domain.node.entity.DecisionLine;
import com.back.domain.node.repository.BaseLineRepository;
import com.back.domain.node.repository.DecisionLineRepository;
import com.back.domain.scenario.dto.*;
import com.back.domain.scenario.entity.Scenario;
import com.back.domain.scenario.entity.ScenarioStatus;
import com.back.domain.scenario.entity.SceneCompare;
import com.back.domain.scenario.repository.ScenarioRepository;
import com.back.domain.scenario.repository.SceneCompareRepository;
import com.back.domain.scenario.repository.SceneTypeRepository;
import com.back.global.ai.service.AiService;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
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
@Slf4j
public class ScenarioService {

    // Repository 주입
    private final ScenarioRepository scenarioRepository;
    private final SceneTypeRepository sceneTypeRepository;
    private final SceneCompareRepository sceneCompareRepository;

    // Node Repository 주입
    private final DecisionLineRepository decisionLineRepository;
    private final BaseLineRepository baseLineRepository;

    // Object Mapper 주입
    private final ObjectMapper objectMapper;

    // AI Service 주입 (추후 구현 시 필요, AI 호출용)
    private final AiService aiService;

    // 시나리오 생성
    @Transactional
    public ScenarioStatusResponse createScenario(Long userId, ScenarioCreateRequest request) {
        // DecisionLine 존재 여부 확인
        DecisionLine decisionLine = decisionLineRepository.findById(request.decisionLineId())
                .orElseThrow(() -> new ApiException(ErrorCode.DECISION_LINE_NOT_FOUND));

        // 권한 검증 (DecisionLine 소유자와 요청자 일치 여부)
        if (!decisionLine.getUser().getId().equals(userId)) {
            throw new ApiException(ErrorCode.HANDLE_ACCESS_DENIED);
        }

        // 시나리오 중복 생성 방지 (PENDING 또는 PROCESSING 상태인 시나리오가 이미 존재하는지 확인)
        if (scenarioRepository.existsByDecisionLineIdAndStatus(request.decisionLineId(), ScenarioStatus.PENDING) ||
            scenarioRepository.existsByDecisionLineIdAndStatus(request.decisionLineId(), ScenarioStatus.PROCESSING)) {
            throw new ApiException(ErrorCode.SCENARIO_ALREADY_IN_PROGRESS);
        }

        // 베이스 시나리오 존재 여부 확인 및 생성
        if (!scenarioRepository.existsByDecisionLine_BaseLineId(decisionLine.getBaseLine().getId())) {
            // 베이스 시나리오가 없으면 새로 생성
            createBaseScenario(decisionLine.getBaseLine());
        }

        // 시나리오 엔티티 생성 및 저장 (초기 상태는 PENDING)
        Scenario scenario = Scenario.builder()
                .user(decisionLine.getUser())
                .decisionLine(decisionLine)
                .status(ScenarioStatus.PENDING)
                .build();
        Scenario savedScenario = scenarioRepository.save(scenario);

        // 비동기 방식으로 AI 시나리오 생성 (현재는 Mock 구현)
        processScenarioGenerationAsync(savedScenario.getId());

        // DTO 변환 및 반환
        return new ScenarioStatusResponse(
                savedScenario.getId(),
                savedScenario.getStatus(),
                "시나리오 생성이 시작되었습니다."
        );
    }

    // 시나리오 생성 Helper 메서드
    // 베이스 시나리오 생성 (Mock 구현)
    @Transactional
    protected void createBaseScenario(BaseLine baseLine) {
        // Mock 베이스 시나리오 데이터 생성
        Scenario baseScenario = Scenario.builder()
                .user(baseLine.getUser())
                .decisionLine(null) // 베이스 시나리오는 DecisionLine 없음
                .status(ScenarioStatus.COMPLETED)
                .job("현재 직업 상태")
                .total(50) // 기본 베이스라인 점수
                .summary("현재 상황을 기반으로 한 베이스 시나리오입니다.")
                .description("사용자의 현재 삶의 상황을 반영한 기준점이 되는 시나리오입니다.")
                .timelineTitles("{\"2020\":\"현재 상황\",\"2025\":\"현재 진로 유지\"}")
                .img("base_scenario_image.jpg")
                .build();

        scenarioRepository.save(baseScenario);

        // 베이스 시나리오용 SceneType 생성
        createBaseSceneTypes(baseScenario);

        // TODO: 실제 구현 시 BaseLine.title 업데이트
        // baseLine.setTitle("AI가 생성한 베이스라인 제목");
        // baseLineRepository.save(baseLine);
    }

    // 베이스 시나리오용 SceneType 생성 (Mock 구현)
    protected void createBaseSceneTypes(Scenario baseScenario) {
        var baseSceneTypes = List.of(
                com.back.domain.scenario.entity.SceneType.builder()
                        .scenario(baseScenario)
                        .type(com.back.domain.scenario.entity.Type.경제)
                        .point(50)
                        .analysis("현재 경제 상황 기준점")
                        .build(),
                com.back.domain.scenario.entity.SceneType.builder()
                        .scenario(baseScenario)
                        .type(com.back.domain.scenario.entity.Type.행복)
                        .point(50)
                        .analysis("현재 행복 수준 기준점")
                        .build(),
                com.back.domain.scenario.entity.SceneType.builder()
                        .scenario(baseScenario)
                        .type(com.back.domain.scenario.entity.Type.관계)
                        .point(50)
                        .analysis("현재 인간관계 기준점")
                        .build(),
                com.back.domain.scenario.entity.SceneType.builder()
                        .scenario(baseScenario)
                        .type(com.back.domain.scenario.entity.Type.직업)
                        .point(50)
                        .analysis("현재 직업 상황 기준점")
                        .build(),
                com.back.domain.scenario.entity.SceneType.builder()
                        .scenario(baseScenario)
                        .type(com.back.domain.scenario.entity.Type.건강)
                        .point(50)
                        .analysis("현재 건강 상태 기준점")
                        .build()
        );

        sceneTypeRepository.saveAll(baseSceneTypes);
    }

    // 비동기 방식으로 AI 시나리오 생성
    @Async
    @Transactional // TODO: AI 연동시 별도 트랜잭션 관리로 변경 필요
    public void processScenarioGenerationAsync(Long scenarioId) {
        // 시나리오 조회
        Scenario scenario = scenarioRepository.findById(scenarioId)
                .orElseThrow(() -> new ApiException(ErrorCode.SCENARIO_NOT_FOUND));

        try {
            // 상태를 PROCESSING으로 업데이트
            scenario.setStatus(ScenarioStatus.PROCESSING);
            scenarioRepository.save(scenario);

            // AI 시나리오 생성 (현재는 Mock 데이터로 대체)
            mockAiScenarioGeneration(scenario);

            // 상태를 COMPLETED로 업데이트
            scenario.setStatus(ScenarioStatus.COMPLETED);
            scenarioRepository.save(scenario);
        } catch (Exception e) {
            // 오류 발생 시 상태를 FAILED로 업데이트하고 오류 메시지 저장
            scenario.setStatus(ScenarioStatus.FAILED);
            scenario.setErrorMessage(e.getMessage());
            scenarioRepository.save(scenario);
        }
    }

    // AI 시나리오 생성 (추후 구현 예정, 현재는 Mock 데이터로 대체)
    @Transactional
    protected void mockAiScenarioGeneration(Scenario scenario) {
        // Mock 데이터로 시나리오 완성
        scenario.setJob("스타트업 CEO");
        scenario.setTotal(415);
        scenario.setSummary("혁신적인 기술로 성공한 창업가");
        scenario.setDescription("상세 시나리오 내용...");
        scenario.setTimelineTitles("{\"2020\":\"창업 시작\",\"2025\":\"상장 성공\"}");
        scenario.setStatus(ScenarioStatus.COMPLETED);

        // 5개 지표 SceneType 생성
        createSceneTypes(scenario);

        // 베이스 시나리오와의 비교 결과 생성
        createSceneCompare(scenario);
    }

    // SceneType 5개 지표 생성 (Mock 구현)
    protected void createSceneTypes(Scenario scenario) {
        // TODO: 실제 구현 시 AI가 생성한 지표별 점수와 분석 사용
        var sceneTypes = List.of(
                com.back.domain.scenario.entity.SceneType.builder()
                        .scenario(scenario)
                        .type(com.back.domain.scenario.entity.Type.경제)
                        .point(90)
                        .analysis("창업 성공으로 경제적 안정성 확보")
                        .build(),
                com.back.domain.scenario.entity.SceneType.builder()
                        .scenario(scenario)
                        .type(com.back.domain.scenario.entity.Type.행복)
                        .point(85)
                        .analysis("자아실현을 통한 높은 만족도")
                        .build(),
                com.back.domain.scenario.entity.SceneType.builder()
                        .scenario(scenario)
                        .type(com.back.domain.scenario.entity.Type.관계)
                        .point(75)
                        .analysis("업무 집중으로 인한 관계 관리 필요")
                        .build(),
                com.back.domain.scenario.entity.SceneType.builder()
                        .scenario(scenario)
                        .type(com.back.domain.scenario.entity.Type.직업)
                        .point(95)
                        .analysis("혁신적 기업 리더로서 높은 성취")
                        .build(),
                com.back.domain.scenario.entity.SceneType.builder()
                        .scenario(scenario)
                        .type(com.back.domain.scenario.entity.Type.건강)
                        .point(70)
                        .analysis("스트레스 관리와 건강 관리 필요")
                        .build()
        );

        sceneTypeRepository.saveAll(sceneTypes);
    }

    // SceneCompare 비교 결과 생성 (Mock 구현)
    protected void createSceneCompare(Scenario scenario) {
        // 베이스 시나리오 조회
        Scenario baseScenario = scenarioRepository
                .findFirstByDecisionLine_BaseLineIdOrderByCreatedDateAsc(
                        scenario.getDecisionLine().getBaseLine().getId())
                .orElse(null);

        if (baseScenario != null) {
            // TODO: 실제 구현 시 AI가 생성한 비교 분석 사용
            var compareResults = List.of(
                    com.back.domain.scenario.entity.SceneCompare.builder()
                            .scenario(scenario)
                            .compareResult("전반적으로 베이스 시나리오 대비 35점 향상된 결과를 보여줍니다.")
                            .resultType(com.back.domain.scenario.entity.SceneCompareResultType.TOTAL)
                            .build(),
                    com.back.domain.scenario.entity.SceneCompare.builder()
                            .scenario(scenario)
                            .compareResult("창업을 통한 경제적 성공으로 40점 향상")
                            .resultType(com.back.domain.scenario.entity.SceneCompareResultType.경제)
                            .build(),
                    com.back.domain.scenario.entity.SceneCompare.builder()
                            .scenario(scenario)
                            .compareResult("자아실현을 통해 35점 향상")
                            .resultType(com.back.domain.scenario.entity.SceneCompareResultType.행복)
                            .build(),
                    com.back.domain.scenario.entity.SceneCompare.builder()
                            .scenario(scenario)
                            .compareResult("업무 집중으로 인한 관계 관리 필요하지만 25점 향상")
                            .resultType(com.back.domain.scenario.entity.SceneCompareResultType.관계)
                            .build(),
                    com.back.domain.scenario.entity.SceneCompare.builder()
                            .scenario(scenario)
                            .compareResult("혁신적 기업 리더로서 45점 대폭 향상")
                            .resultType(com.back.domain.scenario.entity.SceneCompareResultType.직업)
                            .build(),
                    com.back.domain.scenario.entity.SceneCompare.builder()
                            .scenario(scenario)
                            .compareResult("스트레스 증가로 20점 향상에 그쳤으나 관리 가능")
                            .resultType(com.back.domain.scenario.entity.SceneCompareResultType.건강)
                            .build()
            );

            sceneCompareRepository.saveAll(compareResults);
        }
    }

    // 시나리오 생성 상태 조회
    @Transactional(readOnly = true) // TODO: readonly 쓰는 이유 공부하기
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

    // JSON 파싱 Helper 메서드
    private Map<String, String> parseTimelineTitles(String timelineTitles) {
        try {
            // Null 이나 빈 문자열 체크
            if (timelineTitles == null || timelineTitles.trim().isEmpty()) {
                throw new ApiException(ErrorCode.SCENARIO_TIMELINE_NOT_FOUND);
            }

            // JSON 문자열을 Map으로 파싱
            return objectMapper.readValue(timelineTitles,
                    new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            // JSON 파싱 실패 시 예외 처리
            throw new ApiException(ErrorCode.SCENARIO_TIMELINE_NOT_FOUND);
        }
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
}