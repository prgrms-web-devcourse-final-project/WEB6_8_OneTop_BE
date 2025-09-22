/**
 * [API] BaseLine 전용 엔드포인트
 * - 라인 단위 일괄 생성 / 중간 분기점(pivot) 조회
 */
package com.back.domain.node.controller;

import com.back.domain.node.dto.BaseLineBulkCreateRequest;
import com.back.domain.node.dto.BaseLineBulkCreateResponse;
import com.back.domain.node.dto.BaseLineDto;
import com.back.domain.node.dto.PivotListDto;
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

    // GET /api/v1/base-lines/{baseLineId}/nodes
    @GetMapping("/{baseLineId}/nodes")
    public ResponseEntity<List<BaseLineDto>> getBaseLineNodes(@PathVariable Long baseLineId) {
        return ResponseEntity.ok(nodeService.getBaseLineNodes(baseLineId));
    }

    // GET /api/v1/base-lines/node/{baseNodeId}
    @GetMapping("/node/{baseNodeId}")
    public ResponseEntity<BaseLineDto> getBaseNode(@PathVariable Long baseNodeId) {
        return ResponseEntity.ok(nodeService.getBaseNode(baseNodeId));
    }

}
