/**
 * [API] BaseLine 전용 엔드포인트
 * - 라인 단위 일괄 생성 / 중간 분기점(pivot) 조회
 * - 전체 노드 목록 조회 / 단일 노드 조회
 * - 사용자 전체 트리 조회 (베이스/결정 노드 일괄 반환)
 */
package com.back.domain.node.controller;

import com.back.domain.node.dto.*;
import com.back.domain.node.service.NodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/base-lines")
@RequiredArgsConstructor
public class BaseLineController {

    private final NodeService nodeService;

    // 라인 단위 일괄 생성(헤더~꼬리까지)
    @PostMapping("/bulk")
    public ResponseEntity<BaseLineBulkCreateResponse> createBaseLineBulk(@RequestBody BaseLineBulkCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(nodeService.createBaseLineWithNodes(request));
    }

    // 헤더/꼬리 제외 pivot 목록 반환
    @GetMapping("/{baseLineId}/pivots")
    public ResponseEntity<PivotListDto> getPivots(@PathVariable Long baseLineId) {
        return ResponseEntity.ok(nodeService.getPivotBaseNodes(baseLineId));
    }

    // 전체 노드 목록 반환
    @GetMapping("/{baseLineId}/nodes")
    public ResponseEntity<List<BaseNodeDto>> getBaseLineNodes(@PathVariable Long baseLineId) {
        return ResponseEntity.ok(nodeService.getBaseLineNodes(baseLineId));
    }

    // 단일 노드 반환
    @GetMapping("/nodes/{baseNodeId}")
    public ResponseEntity<BaseNodeDto> getBaseNode(@PathVariable Long baseNodeId) {
        return ResponseEntity.ok(nodeService.getBaseNode(baseNodeId));
    }

    // 사용자 전체 트리 조회 (베이스/결정 노드 일괄 반환)
    @GetMapping("/{baseLineId}/tree")
    public ResponseEntity<TreeDto> getTreeForBaseLine(@PathVariable Long baseLineId) {
        // 트리 조회 서비스 호출
        TreeDto tree = nodeService.getTreeForBaseLine(baseLineId);
        return ResponseEntity.ok(tree);
    }
}
