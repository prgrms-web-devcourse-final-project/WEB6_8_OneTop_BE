/**
 * [API] DecisionLine 조회 전용 컨트롤러
 * - 목록: 사용자별 라인 요약
 * - 상세: 라인 메타 + 노드 목록
 */
package com.back.domain.node.controller;

import com.back.domain.node.dto.decision.DecisionLineDetailDto;
import com.back.domain.node.dto.decision.DecisionLineListDto;
import com.back.domain.node.service.NodeQueryService;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import com.back.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/decision-lines")
@RequiredArgsConstructor
public class DecisionLineController {

    private final NodeQueryService nodeQueryService;

    // 가장 중요한: 인증 사용자별 결정 라인 목록(요약)
    @GetMapping
    public ResponseEntity<DecisionLineListDto> list(@AuthenticationPrincipal CustomUserDetails me) {
        if (me == null) throw new ApiException(ErrorCode.HANDLE_ACCESS_DENIED, "login required");
        return ResponseEntity.ok(nodeQueryService.getDecisionLines(me.getId()));
    }

    // 가장 많이 사용하는: 특정 결정 라인 상세
    @GetMapping("/{decisionLineId}")
    public ResponseEntity<DecisionLineDetailDto> detail(@PathVariable Long decisionLineId) {
        return ResponseEntity.ok(nodeQueryService.getDecisionLineDetail(decisionLineId));
    }
}
