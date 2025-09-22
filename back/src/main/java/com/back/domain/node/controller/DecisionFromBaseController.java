/**
 * [API] BaseLine의 pivot에서 DecisionNode 생성
 * - 사용자가 선택한 중간 시점(BaseNode)에서 파생
 */
package com.back.domain.node.controller;

import com.back.domain.node.dto.DecLineDto;
import com.back.domain.node.dto.DecisionNodeFromBaseRequest;
import com.back.domain.node.service.NodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/decision-nodes")
@RequiredArgsConstructor
public class DecisionFromBaseController {

    private final NodeService nodeService;

    // pivot(BaseNode)에서 DecisionNode 생성
    @PostMapping("/from-base")
    public ResponseEntity<DecLineDto> createDecisionFromBase(@RequestBody DecisionNodeFromBaseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(nodeService.createDecisionNodeFromBase(request));
    }
}
