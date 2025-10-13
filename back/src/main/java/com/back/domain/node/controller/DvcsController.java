/**
 * [API] DVCS 하이브리드 컨트롤러
 * - 베이스 편집(커밋/패치), 결정 편집(오버라이드/승격), 팔로우 정책 전환, 브랜치 선택, 브랜치/커밋 조회
 * - 규칙: Controller → Service → Repository (Controller는 얇게 유지)
 */
package com.back.domain.node.controller;

import com.back.domain.node.dto.dvcs.*;
import com.back.domain.node.service.DvcsFacadeService;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import com.back.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
// 이 컨트롤러는 현재 사용하지 않으며 향후 DVCS 를 이용한 라인, 노드 편집 기능이 추가될 때 활성화될 예정임
@RestController
@RequestMapping("/api/v1/dvcs")
@RequiredArgsConstructor
public class DvcsController {

    private final DvcsFacadeService dvcs; // ★ 파사드 서비스

    // 베이스 편집을 브랜치 커밋으로 반영
    @PostMapping("/base/edit")
    public ResponseEntity<EditAcknowledgeDto> editBase(@AuthenticationPrincipal CustomUserDetails me,
                                                       @RequestBody BaseEditRequest req) {
        if (me == null) throw new ApiException(ErrorCode.HANDLE_ACCESS_DENIED, "login required");

        return ResponseEntity.ok(dvcs.editBase(me.getId(), req));
    }

    // 결정 노드 편집(OVERRIDE 또는 업스트림 승격)
    @PostMapping("/decision/edit")
    public ResponseEntity<EditAcknowledgeDto> editDecision(@AuthenticationPrincipal CustomUserDetails me,
                                                           @RequestBody DecisionEditRequest req) {
        if (me == null) throw new ApiException(ErrorCode.HANDLE_ACCESS_DENIED, "login required");

        return ResponseEntity.ok(dvcs.editDecision(me.getId(), req));
    }

    // 팔로우 정책 전환(FOLLOW/PINNED/OVERRIDE) 및 핀 커밋 지정
    @PostMapping("/decision/policy")
    public ResponseEntity<EditAcknowledgeDto> changePolicy(@AuthenticationPrincipal CustomUserDetails me,
                                                           @RequestBody FollowPolicyChangeRequest req) {
        if (me == null) throw new ApiException(ErrorCode.HANDLE_ACCESS_DENIED, "login required");

        return ResponseEntity.ok(dvcs.changePolicy(me.getId(), req));
    }

    // 브랜치 생성 또는 기존 브랜치 선택 후 라인에 적용
    @PostMapping("/branch/select")
    public ResponseEntity<EditAcknowledgeDto> selectBranch(@AuthenticationPrincipal CustomUserDetails me,
                                                           @RequestBody BranchSelectRequest req) {
        if (me == null) throw new ApiException(ErrorCode.HANDLE_ACCESS_DENIED, "login required");

        return ResponseEntity.ok(dvcs.selectBranch(me.getId(), req));
    }

    // 특정 베이스라인의 브랜치/커밋 요약 조회
    @GetMapping("/branches/{baseLineId}")
    public ResponseEntity<List<BranchSummaryDto>> listBranches(@PathVariable Long baseLineId) {
        return ResponseEntity.ok(dvcs.listBranches(baseLineId));
    }
}
