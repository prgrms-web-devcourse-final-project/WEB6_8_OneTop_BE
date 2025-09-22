/**
 * [API] 피벗에서 시작→연속 저장→완료/취소 흐름
 * - from-base: 첫 결정 생성
 * - next: 다음 결정 생성
 * - cancel: 라인 취소(파기)
 * - complete: 라인 완료(잠금)
 */
package com.back.domain.node.controller;

import com.back.domain.node.dto.*;
import com.back.domain.node.service.NodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/decision-flow")
@RequiredArgsConstructor
public class DecisionFlowController {

    private final NodeService nodeService;

    @PostMapping("/from-base")
    public ResponseEntity<DecLineDto> createFromBase(@RequestBody DecisionNodeFromBaseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(nodeService.createDecisionNodeFromBase(request));
    }

    @PostMapping("/next")
    public ResponseEntity<DecLineDto> createNext(@RequestBody DecisionNodeNextRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(nodeService.createDecisionNodeNext(request));
    }

    @PostMapping("/{decisionLineId}/cancel")
    public ResponseEntity<DecisionLineLifecycleDto> cancel(@PathVariable Long decisionLineId) {
        return ResponseEntity.ok(nodeService.cancelDecisionLine(decisionLineId));
    }

    @PostMapping("/{decisionLineId}/complete")
    public ResponseEntity<DecisionLineLifecycleDto> complete(@PathVariable Long decisionLineId) {
        return ResponseEntity.ok(nodeService.completeDecisionLine(decisionLineId));
    }
}
