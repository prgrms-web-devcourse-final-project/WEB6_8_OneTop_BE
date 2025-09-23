package com.back.domain.scenario.controller;

import com.back.domain.scenario.dto.*;
import com.back.domain.scenario.entity.ScenarioStatus;
import com.back.domain.scenario.service.ScenarioService;
import com.back.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

/**
 * 시나리오 관련 API 요청을 처리하는 컨트롤러.
 * 시나리오 추출, 상세 조회, 비교 등의 기능을 제공합니다.
 */
@Tag(name = "Scenario", description = "시나리오 관련 API")
@RestController
@RequestMapping("/api/v1/scenarios")
@RequiredArgsConstructor
public class ScenarioController {

    private final ScenarioService scenarioService;

    @PostMapping
    @Operation(summary = "시나리오 생성", description = "DecisionLine을 기반으로 AI 시나리오를 생성합니다.")
    public ApiResponse<Long> createScenario(
            @Valid @RequestBody ScenarioCreateRequest request,
            Principal principal
            ) {
        // Mock: 실제로는 scenarioService.createScenario(request, principal) 호출
        return ApiResponse.success(1001L, "시나리오 생성 요청이 접수되었습니다.", HttpStatus.CREATED);
    }

    @GetMapping("/{scenarioId}/status")
    @Operation(summary = "시나리오 상태 조회", description = "시나리오 생성 진행 상태를 조회합니다.")
    public ApiResponse<ScenarioStatusResponse> getScenarioStatus(
            @Parameter(description = "시나리오 ID") @PathVariable Long scenarioId
    ) {
        // Mock
        ScenarioStatusResponse mockResponse = new ScenarioStatusResponse(
                scenarioId,
                ScenarioStatus.PROCESSING,
                "AI가 시나리오를 생성 중입니다..."
        );
        return ApiResponse.success(mockResponse, "상태를 성공적으로 조회했습니다.");
    }

    @GetMapping("/info/{scenarioId}")
    @Operation(summary = "시나리오 상세 조회", description = "완성된 시나리오의 상세 정보를 조회합니다.")
    public ApiResponse<ScenarioDetailResponse> getScenarioDetail(
            @Parameter(description = "시나리오 ID") @PathVariable Long scenarioId
    ) {
        // Mock: 실제로는 큰 DTO라서 null로 처리하거나 간단한 Mock 생성
        return ApiResponse.success(null, "시나리오 상세 정보를 성공적으로 조회했습니다.");
    }

    @GetMapping("/{scenarioId}/timeline")
    @Operation(summary = "시나리오 타임라인 조회", description = "시나리오의 선택 경로를 시간순으로 조회합니다.")
    public ApiResponse<TimelineResponse> getScenarioTimeline(
            @Parameter(description = "시나리오 ID") @PathVariable Long scenarioId
    ) {
        // Mock 타임라인 생성
        List<TimelineResponse.TimelineEvent> mockEvents = List.of(
                new TimelineResponse.TimelineEvent(2020, "창업 도전"),
                new TimelineResponse.TimelineEvent(2022, "해외 진출"),
                new TimelineResponse.TimelineEvent(2025, "상장 성공")
        );
        TimelineResponse mockResponse = new TimelineResponse(scenarioId, mockEvents);
        return ApiResponse.success(mockResponse, "타임라인을 성공적으로 조회했습니다.");
    }

    @GetMapping("/baselines")
    @Operation(summary = "베이스라인 목록 조회", description = "사용자의 베이스라인 목록을 조회합니다.")
    public ApiResponse<List<BaselineListResponse>> getBaselines(
            Principal principal
    ) {
        // TODO: 실제 userId 추출 로직 구현 (JWT에서 추출)
        Long userId = 1L; // Mock userId

        List<BaselineListResponse> baselines = scenarioService.getBaselines(userId);
        return ApiResponse.success(baselines, "베이스라인 목록을 성공적으로 조회했습니다.");
    }

    @GetMapping("/compare/{baseId}/{compareId}")
    @Operation(summary = "시나리오 비교 분석 결과 조회", description = "두 시나리오를 비교 분석 결과를 조회합니다.")
    public ApiResponse<ScenarioCompareResponse> compareScenarios(
            @Parameter(description = "기준 시나리오 ID") @PathVariable Long baseId,
            @Parameter(description = "비교 시나리오 ID") @PathVariable Long compareId
    ) {
        // Mock: 복잡한 DTO라서 null 처리
        return ApiResponse.success(null, "시나리오 비교를 성공적으로 조회했습니다.");
    }
}