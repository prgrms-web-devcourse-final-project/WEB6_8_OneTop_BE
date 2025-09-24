package com.back.domain.scenario.controller;

import com.back.domain.scenario.dto.*;
import com.back.domain.scenario.service.ScenarioService;
import com.back.global.common.ApiResponse;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
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

    // TODO: ApiResponse를 ResponseEntity로 변경 예정
    private final ScenarioService scenarioService;

    @PostMapping
    @Operation(summary = "시나리오 생성", description = "DecisionLine을 기반으로 AI 시나리오를 생성합니다.")
    public ApiResponse<ScenarioStatusResponse> createScenario(
            @Valid @RequestBody ScenarioCreateRequest request
            ) {
        Long userId = 1L; // TODO: Principal에서 추출 예정

        ScenarioStatusResponse scenarioCreateResponse = scenarioService.createScenario(userId, request);

        return ApiResponse.success(scenarioCreateResponse, "시나리오 생성 요청이 접수되었습니다.", HttpStatus.CREATED);
    }

    @GetMapping("/{scenarioId}/status")
    @Operation(summary = "시나리오 상태 조회", description = "시나리오 생성 진행 상태를 조회합니다.")
    public ApiResponse<ScenarioStatusResponse> getScenarioStatus(
            @Parameter(description = "시나리오 ID") @PathVariable Long scenarioId
    ) {
        Long userId = 1L; // TODO: Principal에서 추출 예정

        ScenarioStatusResponse scenarioStatusResponse = scenarioService.getScenarioStatus(scenarioId, userId);

        return ApiResponse.success(scenarioStatusResponse, "상태를 성공적으로 조회했습니다.");
    }

    @GetMapping("/info/{scenarioId}")
    @Operation(summary = "시나리오 상세 조회", description = "완성된 시나리오의 상세 정보를 조회합니다.")
    public ApiResponse<ScenarioDetailResponse> getScenarioDetail(
            @Parameter(description = "시나리오 ID") @PathVariable Long scenarioId
    ) {
        Long userId = 1L; // TODO: Principal에서 추출 예정

        ScenarioDetailResponse scenarioDetailResponse = scenarioService.getScenarioDetail(scenarioId, userId);

        return ApiResponse.success(scenarioDetailResponse, "시나리오 상세 정보를 성공적으로 조회했습니다.");
    }

    @GetMapping("/{scenarioId}/timeline")
    @Operation(summary = "시나리오 타임라인 조회", description = "시나리오의 선택 경로를 시간순으로 조회합니다.")
    public ApiResponse<TimelineResponse> getScenarioTimeline(
            @Parameter(description = "시나리오 ID") @PathVariable Long scenarioId
    ) {
        Long userId = 1L; // TODO: Principal에서 추출 예정

        TimelineResponse timelineResponse = scenarioService.getScenarioTimeline(scenarioId, userId);

        return ApiResponse.success(timelineResponse, "타임라인을 성공적으로 조회했습니다.");
    }

    @GetMapping("/baselines")
    @Operation(summary = "베이스라인 목록 조회", description = "사용자의 베이스라인 목록을 조회합니다.")
    public ApiResponse<List<BaselineListResponse>> getBaselines() {
        Long userId = 1L; // TODO: Principal에서 추출 예정

        List<BaselineListResponse> baselines = scenarioService.getBaselines(userId);

        return ApiResponse.success(baselines, "베이스라인 목록을 성공적으로 조회했습니다.");
    }

    @GetMapping("/compare/{baseId}/{compareId}")
    @Operation(summary = "시나리오 비교 분석 결과 조회", description = "두 시나리오를 비교 분석 결과를 조회합니다.")
    public ApiResponse<ScenarioCompareResponse> compareScenarios(
            @Parameter(description = "기준 시나리오 ID") @PathVariable Long baseId,
            @Parameter(description = "비교 시나리오 ID") @PathVariable Long compareId
    ) {
        Long userId = 1L; // TODO: Principal에서 추출 예정

        ScenarioCompareResponse scenarioCompareResponse = scenarioService.compareScenarios(baseId, compareId, userId);

        return ApiResponse.success(scenarioCompareResponse, "시나리오 비교를 성공적으로 조회했습니다.");
    }

    // Principal에서 userId 추출하는 Helper 메서드 (Mock 구현)
    // TODO: JWT 파싱으로 교체 예정
    private Long getUserIdFromPrincipal(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new ApiException(ErrorCode.HANDLE_ACCESS_DENIED);
        }
        // TODO: 실제로는 JWT 토큰에서 userId 추출
        // String token = principal.getName();
        // return jwtProvider.getUserIdFromToken(token);

        return 1L; // Mock userId
    }
}