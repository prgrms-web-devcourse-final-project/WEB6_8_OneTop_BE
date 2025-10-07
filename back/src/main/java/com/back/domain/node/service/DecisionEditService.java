/**
 * [SERVICE] DecisionEditService
 * - 결정 노드 단건 편집(OVERRIDE 전환) 또는 업스트림 승격으로 베이스 브랜치에 반영
 * - FOLLOW/PINNED/OVERRIDE 정책을 전환하고 버전 참조를 관리
 */
package com.back.domain.node.service;

import com.back.domain.node.entity.*;
import com.back.domain.node.repository.BaselineCommitRepository;
import com.back.domain.node.repository.DecisionNodeRepository;
import com.back.domain.node.repository.NodeAtomVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DecisionEditService {

    private final DecisionNodeRepository decisionNodeRepo;
    private final NodeAtomVersionRepository versionRepo;
    private final DVCSBaseService dvcsBaseService;
    private final VersionResolver versionResolver;
    private final BaselineCommitRepository baselineCommitRepo;


    // 선택된 결정 노드를 OVERRIDE로 전환하고 새 버전을 부여
    @Transactional
    public void overrideDecisionNode(Long decisionNodeId, DecisionEditPayload edit) {
        DecisionNode dn = decisionNodeRepo.findById(decisionNodeId)
                .orElseThrow(() -> new IllegalArgumentException("decision node not found: " + decisionNodeId));

        Long baseVersionId = versionResolver.resolveVersionId(
                dn.getDecisionLine().getBaseLine().getId(),
                dn.getDecisionLine().getBaseBranch() != null ? dn.getDecisionLine().getBaseBranch().getId() : null,
                dn.getDecisionLine().getPinnedCommit() != null ? dn.getDecisionLine().getPinnedCommit().getId() : null,
                FollowPolicy.FOLLOW, // 현재 해석 기준값을 베이스 최신으로 보고 fork
                dn.getAgeYear(),
                null
        );
        if (baseVersionId == null) throw new IllegalStateException("effective version not resolvable");

        NodeAtomVersion before = versionRepo.findById(baseVersionId)
                .orElseThrow(() -> new IllegalArgumentException("base version not found: " + baseVersionId));

        NodeAtomVersion after = versionRepo.save(before.forkWith(
                edit.category(), edit.situation(), edit.decision(),
                edit.optionsJson(), edit.description(), dn.getAgeYear(), edit.contentHash()
        ));

        dn.setOverride(after);
        decisionNodeRepo.save(dn);
    }

    // 결정 노드의 편집을 베이스 브랜치 커밋으로 승격하여 FOLLOW 전 라인에 반영
    @Transactional
    public void promoteEditToBase(Long decisionNodeId, DecisionEditPayload edit, String message) {
        DecisionNode dn = decisionNodeRepo.findById(decisionNodeId)
                .orElseThrow(() -> new IllegalArgumentException("decision node not found: " + decisionNodeId));

        Long baseLineId = dn.getDecisionLine().getBaseLine().getId();
        Long branchId = dn.getDecisionLine().getBaseBranch() != null ? dn.getDecisionLine().getBaseBranch().getId() : null;
        if (branchId == null) throw new IllegalStateException("decision line has no baseBranch");

        dvcsBaseService.commitBaseEdit(
                baseLineId,
                branchId,
                dn.getAgeYear(),
                new DVCSBaseService.BaseEditPayload(
                        edit.category(), edit.situation(), edit.decision(),
                        edit.optionsJson(), edit.description(), edit.contentHash()
                ),
                dn.getUser().getId(),
                message
        );
    }

    // 정책 전환(FOLLOW/PINNED/OVERRIDE)을 수행
    @Transactional
    public void changeFollowPolicy(Long decisionNodeId, FollowPolicy policy) {
        DecisionNode dn = decisionNodeRepo.findById(decisionNodeId)
                .orElseThrow(() -> new IllegalArgumentException("decision node not found: " + decisionNodeId));
        dn.setFollowPolicy(policy);
        if (policy != FollowPolicy.OVERRIDE) {
            dn.setOverrideVersion(null);
        }
        decisionNodeRepo.save(dn);
    }

    // 커밋 핀 설정(PINNED)
    @Transactional
    public void pinToCommit(Long decisionNodeId, Long pinnedCommitId) {
        DecisionNode dn = decisionNodeRepo.findById(decisionNodeId)
                .orElseThrow(() -> new IllegalArgumentException("decision node not found: " + decisionNodeId));
        BaselineCommit commit = baselineCommitRepo.findById(pinnedCommitId)
                .orElseThrow(() -> new IllegalArgumentException("commit not found: " + pinnedCommitId));
        if (!commit.getBranch().getBaseLine().getId().equals(dn.getDecisionLine().getBaseLine().getId())) {
            throw new IllegalArgumentException("commit/baseLine mismatch");
        }
        dn.setFollowPolicy(FollowPolicy.PINNED);
        dn.getDecisionLine().setPinnedCommit(commit);
        decisionNodeRepo.save(dn);
    }

    // 결정 편집 페이로드를 담는 단순 컨테이너
    public record DecisionEditPayload(
            NodeCategory category,
            String situation,
            String decision,
            String optionsJson,
            String description,
            String contentHash
    ) {}
}
