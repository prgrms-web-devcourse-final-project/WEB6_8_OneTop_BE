/**
 * [API] 피벗에서 시작→연속 저장→완료/취소 흐름
 * - from-base: 첫 결정 생성(서버가 피벗 노드 해석)
 * - next: 다음 결정 생성(라인은 부모에서 해석)
 * - cancel: 라인 취소(파기)
 * - complete: 라인 완료(잠금)
 */
package com.back.domain.node.controller;

import com.back.domain.node.dto.DecLineDto;
import com.back.domain.node.dto.DecisionLineLifecycleDto;
import com.back.domain.node.dto.DecisionNodeFromBaseRequest;
import com.back.domain.node.dto.DecisionNodeNextRequest;
import com.back.domain.node.service.NodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/decision-flow")
@RequiredArgsConstructor
public class DecisionFlowController {

    private final NodeService nodeService;

    // from-base: 라인+피벗(순번/나이)+분기슬롯 인덱스만 받아 서버가 pivotBaseNodeId를 해석
    @PostMapping("/from-base")
    public ResponseEntity<DecLineDto> createFromBase(@RequestBody DecisionNodeFromBaseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(nodeService.createDecisionNodeFromBase(request));
    }

    // next: 부모 결정 id만 받아 서버가 라인/다음 나이/베이스 매칭을 해석
    @PostMapping("/next")
    public ResponseEntity<DecLineDto> createNext(@RequestBody DecisionNodeNextRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(nodeService.createDecisionNodeNext(request));
    }

    // 라인 취소
    @PostMapping("/{decisionLineId}/cancel")
    public ResponseEntity<DecisionLineLifecycleDto> cancel(@PathVariable Long decisionLineId) {
        return ResponseEntity.ok(nodeService.cancelDecisionLine(decisionLineId));
    }

    // 라인 완료
    @PostMapping("/{decisionLineId}/complete")
    public ResponseEntity<DecisionLineLifecycleDto> complete(@PathVariable Long decisionLineId) {
        return ResponseEntity.ok(nodeService.completeDecisionLine(decisionLineId));
    }
}
