/**
 * [API] 피벗에서 시작→연속 저장→완료/취소 흐름
 * - from-base: 첫 결정 생성(서버가 피벗 노드 해석)
 * - next: 다음 결정 생성(라인은 부모에서 해석)
 * - cancel: 라인 취소(파기)
 * - complete: 라인 완료(잠금)
 */
package com.back.domain.node.controller;

import com.back.domain.node.dto.decision.*;
import com.back.domain.node.service.NodeService;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import com.back.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/decision-flow")
@RequiredArgsConstructor
public class DecisionFlowController {

    private final NodeService nodeService;

    // from-base: 라인+피벗(순번/나이)+분기슬롯 인덱스만 받아 서버가 pivotBaseNodeId를 해석
    @PostMapping("/from-base")
    public ResponseEntity<DecNodeDto> createFromBase(@Valid @RequestBody DecisionNodeFromBaseRequest request,
                                                     @AuthenticationPrincipal CustomUserDetails me) {
        if (me == null) throw new ApiException(ErrorCode.HANDLE_ACCESS_DENIED, "login required");
        return ResponseEntity.status(HttpStatus.CREATED).body(nodeService.createDecisionNodeFromBase(request));
    }

    // next: 부모 결정 id만 받아 서버가 라인/다음 나이/베이스 매칭을 해석
    @PostMapping("/next")
    public ResponseEntity<DecNodeDto> createNext(@AuthenticationPrincipal CustomUserDetails me,
                                                 @RequestBody DecisionNodeNextRequest request) {
        if (me == null) throw new ApiException(ErrorCode.HANDLE_ACCESS_DENIED, "login required");
        return ResponseEntity.status(HttpStatus.CREATED).body(nodeService.createDecisionNodeNext(request));
    }

    // 라인 취소
    @PostMapping("/{decisionLineId}/cancel")
    public ResponseEntity<DecisionLineLifecycleDto> cancel(@AuthenticationPrincipal CustomUserDetails me,
                                                           @PathVariable Long decisionLineId) {
        if (me == null) throw new ApiException(ErrorCode.HANDLE_ACCESS_DENIED, "login required");
        return ResponseEntity.ok(nodeService.cancelDecisionLine(decisionLineId));
    }

    // 라인 완료
    @PostMapping("/{decisionLineId}/complete")
    public ResponseEntity<DecisionLineLifecycleDto> complete(@AuthenticationPrincipal CustomUserDetails me,
                                                             @PathVariable Long decisionLineId) {
        if (me == null) throw new ApiException(ErrorCode.HANDLE_ACCESS_DENIED, "login required");
        return ResponseEntity.ok(nodeService.completeDecisionLine(decisionLineId));
    }

    // 분기점까지의 라인 복제(포크) 및 새로운 라인 생성
    @PostMapping("/fork")
    public ResponseEntity<DecNodeDto> fork(@AuthenticationPrincipal CustomUserDetails me,
                                           @RequestBody ForkFromDecisionRequest request) {
        if (me == null) throw new ApiException(ErrorCode.HANDLE_ACCESS_DENIED, "login required");
        return ResponseEntity.status(HttpStatus.CREATED).body(nodeService.forkFromDecision(request));
    }
}
