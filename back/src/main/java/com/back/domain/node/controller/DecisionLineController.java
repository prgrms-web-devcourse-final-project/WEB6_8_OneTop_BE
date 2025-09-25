/**
 * [API] DecisionLine 조회 전용 컨트롤러
 * - 목록: 사용자별 라인 요약
 * - 상세: 라인 메타 + 노드 목록
 */
package com.back.domain.node.controller;

import com.back.domain.node.dto.DecisionLineDetailDto;
import com.back.domain.node.dto.DecisionLineListDto;
import com.back.domain.node.service.NodeQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/decision-lines")
@RequiredArgsConstructor
public class DecisionLineController {

    private final NodeQueryService nodeQueryService;

    // 가장 중요한: 사용자별 결정 라인 목록(요약)
    @GetMapping
    public ResponseEntity<DecisionLineListDto> list(@RequestParam Long userId) {
        return ResponseEntity.ok(nodeQueryService.getDecisionLines(userId));
    }

    // 가장 많이 사용하는: 특정 결정 라인 상세
    @GetMapping("/{decisionLineId}")
    public ResponseEntity<DecisionLineDetailDto> detail(@PathVariable Long decisionLineId) {
        return ResponseEntity.ok(nodeQueryService.getDecisionLineDetail(decisionLineId));
    }
}
