package com.back.domain.scenario.controller;

import com.back.domain.scenario.dto.*;
import com.back.domain.scenario.service.ScenarioService;
import com.back.global.common.PageResponse;
import com.back.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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

    /**
     * 인증된 사용자의 ID를 안전하게 추출합니다.
     * 테스트 환경에서 userDetails가 null일 수 있으므로 기본값을 제공합니다.
     */
    private Long getUserId(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getUser() == null) {
            // 테스트 환경이나 인증이 비활성화된 환경에서는 기본 사용자 ID 사용
            return 1L;
        }
        return userDetails.getUser().getId();
    }

    @PostMapping
    @Operation(summary = "시나리오 생성", description = "DecisionLine을 기반으로 AI 시나리오를 생성합니다.")
    public ResponseEntity<ScenarioStatusResponse> createScenario(
            @Valid @RequestBody ScenarioCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails
            ) {
        Long userId = getUserId(userDetails);

        ScenarioStatusResponse scenarioCreateResponse = scenarioService.createScenario(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(scenarioCreateResponse);
    }

    @GetMapping("/{scenarioId}/status")
    @Operation(summary = "시나리오 상태 조회", description = "시나리오 생성 진행 상태를 조회합니다.")
    public ResponseEntity<ScenarioStatusResponse> getScenarioStatus(
            @Parameter(description = "시나리오 ID") @PathVariable Long scenarioId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long userId = getUserId(userDetails);

        ScenarioStatusResponse scenarioStatusResponse = scenarioService.getScenarioStatus(scenarioId, userId);

        return ResponseEntity.ok(scenarioStatusResponse);
    }

    @GetMapping("/info/{scenarioId}")
    @Operation(summary = "시나리오 상세 조회", description = "완성된 시나리오의 상세 정보를 조회합니다.")
    public ResponseEntity<ScenarioDetailResponse> getScenarioDetail(
            @Parameter(description = "시나리오 ID") @PathVariable Long scenarioId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long userId = getUserId(userDetails);

        ScenarioDetailResponse scenarioDetailResponse = scenarioService.getScenarioDetail(scenarioId, userId);

        return ResponseEntity.ok(scenarioDetailResponse);
    }

    @GetMapping("/{scenarioId}/timeline")
    @Operation(summary = "시나리오 타임라인 조회", description = "시나리오의 선택 경로를 시간순으로 조회합니다.")
    public ResponseEntity<TimelineResponse> getScenarioTimeline(
            @Parameter(description = "시나리오 ID") @PathVariable Long scenarioId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long userId = getUserId(userDetails);

        TimelineResponse timelineResponse = scenarioService.getScenarioTimeline(scenarioId, userId);

        return ResponseEntity.ok(timelineResponse);
    }

    @GetMapping("/baselines")
    @Operation(summary = "베이스라인 목록 조회", description = "사용자의 베이스라인 목록을 페이지네이션으로 조회합니다. (1-based 페이지네이션)")
    public ResponseEntity<PageResponse<BaselineListResponse>> getBaselines(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Pageable pageable
    ) {
        Long userId = getUserId(userDetails);

        PageResponse<BaselineListResponse> baselines = scenarioService.getBaselines(userId, pageable);

        return ResponseEntity.ok(baselines);
    }

    @GetMapping("/compare/{baseId}/{compareId}")
    @Operation(summary = "시나리오 비교 분석 결과 조회", description = "두 시나리오를 비교 분석 결과를 조회합니다.")
    public ResponseEntity<ScenarioCompareResponse> compareScenarios(
            @Parameter(description = "기준 시나리오 ID") @PathVariable Long baseId,
            @Parameter(description = "비교 시나리오 ID") @PathVariable Long compareId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long userId = getUserId(userDetails);

        ScenarioCompareResponse scenarioCompareResponse = scenarioService.compareScenarios(baseId, compareId, userId);

        return ResponseEntity.ok(scenarioCompareResponse);
    }
}