/**
 * [API] BaseLine 전용 엔드포인트
 * - 라인 단위 일괄 생성 / 중간 분기점(pivot) 조회
 * - 전체 노드 목록 조회 / 단일 노드 조회
 * - 사용자 전체 트리 조회 (베이스/결정 노드 일괄 반환)
 */
package com.back.domain.node.controller;

import com.back.domain.node.dto.PivotListDto;
import com.back.domain.node.dto.TreeDto;
import com.back.domain.node.dto.base.BaseLineBulkCreateRequest;
import com.back.domain.node.dto.base.BaseLineBulkCreateResponse;
import com.back.domain.node.dto.base.BaseLineDto;
import com.back.domain.node.dto.base.BaseNodeDto;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/base-lines")
@RequiredArgsConstructor
public class BaseLineController {

    private final NodeService nodeService;

    // 라인 단위 일괄 생성(헤더~꼬리까지)
    @PostMapping("/bulk")
    public ResponseEntity<BaseLineBulkCreateResponse> createBaseLineBulk(@AuthenticationPrincipal CustomUserDetails me,
                                                                         @Valid @RequestBody BaseLineBulkCreateRequest request) {
        if (me == null) throw new ApiException(ErrorCode.HANDLE_ACCESS_DENIED, "login required");
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
        TreeDto tree = nodeService.getTreeForBaseLine(baseLineId);
        return ResponseEntity.ok(tree);
    }

    // 내가 만든 베이스라인 목록 조회
    @GetMapping("/mine")
    public ResponseEntity<List<BaseLineDto>> getMyBaseLines(
            @AuthenticationPrincipal CustomUserDetails me
    ) {
        if (me == null) throw new ApiException(ErrorCode.HANDLE_ACCESS_DENIED, "login required");

        List<BaseLineDto> list = nodeService.getMyBaseLines(me.getId());
        return ResponseEntity.ok(list);
    }

    // 한줄 요약: 소유자 검증 후 베이스라인과 모든 연관 데이터 일괄 삭제
    @DeleteMapping("/{baseLineId}")
    public ResponseEntity<Void> deleteBaseLine(
            @AuthenticationPrincipal CustomUserDetails me,
            @PathVariable Long baseLineId
    ) {
        if (me == null) throw new ApiException(ErrorCode.HANDLE_ACCESS_DENIED, "login required");
        nodeService.deleteBaseLineDeep(me.getId(), baseLineId); // 가장 많이 사용하는 호출: 파사드에 위임
        return ResponseEntity.noContent().build();
    }
}
