/**
 * [SERVICE] VersionResolver
 * - Follow/Pinned/Override 정책에 따라 화면에 표시할 최종 NodeAtomVersion을 해석
 * - 커밋 체인을 부모로 거슬러 올라가며 ageYear에 대한 마지막 패치를 선택
 * - 해석 실패 시 BaseNode.currentVersion으로 폴백(초기 마이그레이션 보호)
 */
package com.back.domain.node.service;

import com.back.domain.node.entity.*;
import com.back.domain.node.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VersionResolver {

    private final BaselineBranchRepository branchRepo;
    private final BaselineCommitRepository commitRepo;
    private final BaselinePatchRepository patchRepo;
    private final BaseNodeRepository baseNodeRepo;

    // 정책/브랜치/커밋 상황에 맞춰 최종 버전 id를 해석(브랜치 없으면 BaseLine.currentVersion 폴백)
    public Long resolveVersionId(Long baseLineId,
                                 Long baseBranchId,
                                 Long pinnedCommitId,
                                 FollowPolicy policy,
                                 Integer ageYear,
                                 Long overrideVersionId) {

        // OVERRIDE: 지정 버전 그대로
        if (policy == FollowPolicy.OVERRIDE) {
            return overrideVersionId;
        }

        // PINNED: 고정 커밋 체인 → 폴백(Base.currentVersion)
        if (policy == FollowPolicy.PINNED) {
            return resolveFromCommitChain(ageYear, pinnedCommitId)
                    .orElseGet(() -> resolveFromBaseCurrent(baseLineId, ageYear).orElse(null));
        }

        // FOLLOW: 브랜치/헤드 커밋이 없으면 안전 폴백
        if (policy == FollowPolicy.FOLLOW) {
            if (baseBranchId == null) {
                // 가장 많이 쓰는 호출: Base.currentVersion 폴백
                return resolveFromBaseCurrent(baseLineId, ageYear).orElse(null);
            }

            // 브랜치 헤드 커밋 id 조회
            Long headCommitId = branchRepo.findById(baseBranchId)
                    .map(BaselineBranch::getHeadCommit)
                    .map(BaselineCommit::getId)
                    .orElse(null);

            if (headCommitId == null) {
                // 헤드가 없으면 폴백
                return resolveFromBaseCurrent(baseLineId, ageYear).orElse(null);
            }

            // 커밋 체인에서 ageYear 패치 탐색 → 실패 시 폴백
            return resolveFromCommitChain(ageYear, headCommitId)
                    .orElseGet(() -> resolveFromBaseCurrent(baseLineId, ageYear).orElse(null));
        }

        // 이외 방어적 폴백
        return resolveFromBaseCurrent(baseLineId, ageYear).orElse(null);
    }

    // 커밋 체인을 따라 ageYear 패치의 afterVersion을 찾음
    private Optional<Long> resolveFromCommitChain(Integer ageYear, Long commitId) {
        if (commitId == null || ageYear == null) return Optional.empty();

        BaselineCommit cur = commitRepo.findById(commitId).orElse(null);
        while (cur != null) {
            List<BaselinePatch> patches = patchRepo.findByCommit_IdOrderByIdAsc(cur.getId());
            for (int i = patches.size() - 1; i >= 0; i--) {
                BaselinePatch p = patches.get(i);
                if (ageYear.equals(p.getAgeYear()) && p.getAfterVersion() != null) {
                    return Optional.ofNullable(p.getAfterVersion().getId());
                }
            }
            cur = cur.getParentCommit();
        }
        return Optional.empty();
    }

    // BaseLine의 해당 ageYear에 해당하는 BaseNode.currentVersion으로 폴백
    private Optional<Long> resolveFromBaseCurrent(Long baseLineId, Integer ageYear) {
        if (baseLineId == null || ageYear == null) return Optional.empty();
        List<Long> ids = new ArrayList<>();
        baseNodeRepo.findByBaseLine_IdOrderByAgeYearAscIdAsc(baseLineId).forEach(b -> {
            if (b.getAgeYear() == ageYear && b.getCurrentVersion() != null) {
                ids.add(b.getCurrentVersion().getId());
            }
        });
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(0));
    }
}
