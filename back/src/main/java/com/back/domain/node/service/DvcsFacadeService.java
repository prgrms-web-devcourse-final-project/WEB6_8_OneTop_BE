/**
 * [SERVICE FACADE] DVCS 파사드 서비스
 * - 트랜잭션 경계, 소유자 검증(NodeDomainSupport), 필요한 연관 로딩(EntityGraph) 책임
 * - 컨트롤러는 이 서비스만 호출
 */
package com.back.domain.node.service;

import com.back.domain.node.dto.dvcs.*;
import com.back.domain.node.entity.*;
import com.back.domain.node.repository.*;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DvcsFacadeService {

    private final DVCSBaseService baseService;
    private final DecisionEditService decisionEditService;
    private final NodeDomainSupport support;

    private final BaseLineRepository baseLineRepo;
    private final DecisionNodeRepository decisionNodeRepo;
    private final DecisionLineRepository decisionLineRepo;
    private final BaselineBranchRepository branchRepo;
    private final BaselineCommitRepository commitRepo;

    // 가장 중요한: 베이스 편집 → 커밋 생성
    @Transactional
    public EditAcknowledgeDto editBase(Long meId, BaseEditRequest req) {
        BaseLine bl = baseLineRepo.findWithUserById(req.baseLineId())
                .orElseThrow(() -> new ApiException(ErrorCode.BASE_LINE_NOT_FOUND, "BaseLine not found: " + req.baseLineId()));
        // 소유자 검증(세션 열려있고 user 로딩됨)
        support.ensureOwner(meId, bl.getUser().getId());

        var commit = baseService.commitBaseEdit(
                req.baseLineId(),
                req.branchId(),
                req.ageYear(),
                new DVCSBaseService.BaseEditPayload(
                        req.category(), req.situation(), req.decision(),
                        req.optionsJson(), req.description(), req.contentHash()
                ),
                bl.getUser().getId(),
                req.message()
        );
        return new EditAcknowledgeDto(true, "base edited", commit.getId());
    }

    // 가장 중요한: 결정 편집(override or promote)
    @Transactional
    public EditAcknowledgeDto editDecision(Long meId, DecisionEditRequest req) {
        // ★ LAZY 안전: 라인+유저까지 함께 로딩
        DecisionNode dn = decisionNodeRepo.findWithLineAndUserById(req.decisionNodeId())
                .orElseThrow(() -> new ApiException(ErrorCode.NODE_NOT_FOUND, "DecisionNode not found: " + req.decisionNodeId()));
        support.ensureOwnerOfDecisionLine(meId, dn.getDecisionLine());

        var payload = new DecisionEditService.DecisionEditPayload(
                req.category(), req.situation(), req.decision(),
                req.optionsJson(), req.description(), req.contentHash()
        );

        if (req.promoteToBase()) {
            decisionEditService.promoteEditToBase(dn.getId(), payload, req.message());
            return new EditAcknowledgeDto(true, "promoted to base", dn.getId());
        } else {
            decisionEditService.overrideDecisionNode(dn.getId(), payload);
            return new EditAcknowledgeDto(true, "overridden on line", dn.getId());
        }
    }

    // 팔로우 정책 전환 및 핀 고정
    @Transactional
    public EditAcknowledgeDto changePolicy(Long meId, FollowPolicyChangeRequest req) {
        DecisionNode dn = decisionNodeRepo.findWithLineAndUserById(req.decisionNodeId())
                .orElseThrow(() -> new ApiException(ErrorCode.NODE_NOT_FOUND, "DecisionNode not found: " + req.decisionNodeId()));
        support.ensureOwnerOfDecisionLine(meId, dn.getDecisionLine());

        decisionEditService.changeFollowPolicy(dn.getId(), req.policy());
        if (req.policy() == FollowPolicy.PINNED && req.pinnedCommitId() != null) {
            decisionEditService.pinToCommit(dn.getId(), req.pinnedCommitId());
        }
        return new EditAcknowledgeDto(true, "policy updated", dn.getId());
    }

    // 브랜치 생성/선택 후 라인에 적용(헤드 커밋까지 보장)
    @Transactional
    public EditAcknowledgeDto selectBranch(Long meId, BranchSelectRequest req) {
        // 한줄 요약: 소유자 검증 후 대상 브랜치를 확보하고 headCommit 보장, 필요 시 라인에 부착
        BaseLine bl = baseLineRepo.findWithUserById(req.baseLineId())
                .orElseThrow(() -> new ApiException(ErrorCode.BASE_LINE_NOT_FOUND, "BaseLine not found: " + req.baseLineId()));
        support.ensureOwner(meId, bl.getUser().getId());

        BaselineBranch target;

        if (req.useBranchId() != null) {
            // 기존 브랜치 선택
            target = branchRepo.findById(req.useBranchId())
                    .orElseThrow(() -> new ApiException(ErrorCode.INVALID_INPUT_VALUE, "branch not found: " + req.useBranchId()));
            if (!target.getBaseLine().getId().equals(bl.getId())) {
                throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "branch/baseLine mismatch");
            }
        } else {
            // 이름으로 찾아보고 없으면 생성
            String name = (req.name() != null && !req.name().isBlank()) ? req.name().trim() : ("branch-" + System.currentTimeMillis());
            target = branchRepo.findByBaseLine_IdAndName(bl.getId(), name)
                    .orElseGet(() -> branchRepo.save(BaselineBranch.builder()
                            .baseLine(bl)
                            .name(name)
                            .headCommit(null)
                            .build()));
        }

        // 한줄 요약(가장 많이 쓰는 호출): 브랜치 headCommit 보장(main 헤드 승계, 없으면 init 커밋 생성)
        ensureBranchHeadInitialized(target, bl.getUser().getId());

        // 선택적으로: 특정 결정 라인에 적용
        if (req.decisionLineId() != null) {
            DecisionLine line = decisionLineRepo.findWithUserById(req.decisionLineId())
                    .orElseThrow(() -> new ApiException(ErrorCode.DECISION_LINE_NOT_FOUND, "DecisionLine not found: " + req.decisionLineId()));
            support.ensureOwner(meId, line.getUser().getId());
            line.setBaseBranch(target);
            // PINNED 라인이었다면 정책 유지/무시 정책은 도메인 정책에 따르되, 여기선 변경하지 않음
            decisionLineRepo.save(line);
        }

        return new EditAcknowledgeDto(true, "branch selected", target.getId());
    }


    // 브랜치의 headCommit이 비어 있으면 main의 head를 승계하고, 그것도 없으면 init 커밋을 만든다
    private void ensureBranchHeadInitialized(BaselineBranch branch, Long authorUserId) {
        if (branch.getHeadCommit() != null) return;

        // 같은 BaseLine의 main 브랜치 head를 우선 승계
        BaselineCommit mainHead = branchRepo.findByBaseLine_Id(branch.getBaseLine().getId()).stream()
                .filter(b -> "main".equals(b.getName()))
                .map(BaselineBranch::getHeadCommit)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        if (mainHead != null) {
            branch.moveHeadTo(mainHead);
            branchRepo.save(branch);
            return;
        }

        // main 헤드도 없다면 현재 브랜치에 init 커밋 생성
        BaselineCommit init = commitRepo.save(BaselineCommit.newCommit(branch, null, authorUserId, "init"));
        branch.moveHeadTo(init);
        branchRepo.save(branch);
    }


    // 브랜치/커밋 요약 조회
    public List<BranchSummaryDto> listBranches(Long baseLineId) {
        BaseLine bl = baseLineRepo.findById(baseLineId)
                .orElseThrow(() -> new ApiException(ErrorCode.BASE_LINE_NOT_FOUND, "BaseLine not found: " + baseLineId));

        var branches = branchRepo.findByBaseLine_Id(bl.getId());
        List<BranchSummaryDto> out = new ArrayList<>(branches.size());
        for (BaselineBranch b : branches) {
            var commits = commitRepo.findByBranch_IdOrderByIdDesc(b.getId());
            List<BranchSummaryDto.CommitSummary> cs = new ArrayList<>(commits.size());
            for (BaselineCommit c : commits) {
                cs.add(new BranchSummaryDto.CommitSummary(
                        c.getId(),
                        c.getParentCommit() != null ? c.getParentCommit().getId() : null,
                        c.getAuthorUserId(),
                        c.getMessage(),
                        c.getCreatedDate()
                ));
            }
            out.add(new BranchSummaryDto(
                    b.getId(),
                    bl.getId(),
                    b.getName(),
                    b.getHeadCommit() != null ? b.getHeadCommit().getId() : null,
                    cs
            ));
        }
        return out;
    }
}
