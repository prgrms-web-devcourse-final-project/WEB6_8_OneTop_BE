/**
 * [SERVICE] DVCSBaseService
 * - 베이스 편집을 커밋/패치로 기록하고 브랜치 헤드를 이동
 * - BaseNode.currentVersion을 afterVersion으로 동기화하여 FOLLOW 라인이 즉시 최신을 보게 함
 */
package com.back.domain.node.service;

import com.back.domain.node.entity.*;
import com.back.domain.node.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DVCSBaseService {

    private final BaselineBranchRepository branchRepo;
    private final BaselineCommitRepository commitRepo;
    private final BaselinePatchRepository patchRepo;
    private final NodeAtomRepository atomRepo;
    private final NodeAtomVersionRepository versionRepo;
    private final BaseNodeRepository baseNodeRepo;

    // 베이스 노드 수정 내용을 브랜치 커밋으로 반영
    @Transactional
    public BaselineCommit commitBaseEdit(Long baseLineId,
                                         Long branchId,
                                         Integer ageYear,
                                         BaseEditPayload edit,
                                         Long authorUserId,
                                         String message) {

        BaselineBranch br = branchRepo.findById(branchId)
                .orElseThrow(() -> new IllegalArgumentException("branch not found: " + branchId));
        if (!br.getBaseLine().getId().equals(baseLineId)) {
            throw new IllegalArgumentException("branch/baseLine mismatch");
        }

        BaseNode targetBase = baseNodeRepo.findByBaseLine_IdOrderByAgeYearAscIdAsc(baseLineId)
                .stream().filter(b -> b.getAgeYear() == ageYear)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("base node at age not found: " + ageYear));

        NodeAtomVersion before = Optional.ofNullable(targetBase.getCurrentVersion())
                .orElseThrow(() -> new IllegalStateException("currentVersion not set for base node " + targetBase.getId()));

        NodeAtomVersion after = versionRepo.save(before.forkWith(
                edit.category(), edit.situation(), edit.decision(),
                edit.optionsJson(), edit.description(), ageYear, edit.contentHash()
        ));

        BaselineCommit commit = commitRepo.save(BaselineCommit.newCommit(br, br.getHeadCommit(), authorUserId, message));
        patchRepo.save(BaselinePatch.of(commit, ageYear, before, after));

        br.moveHeadTo(commit);
        branchRepo.save(br);

        targetBase.setCurrentVersion(after);
        baseNodeRepo.save(targetBase);

        return commit;
    }

    // 옵션: BaseNode.currentVersion을 직접 지정하여 초기화(마이그레이션 유틸)
    @Transactional
    public void attachCurrentVersion(Long baseNodeId, Long versionId) {
        BaseNode node = baseNodeRepo.findById(baseNodeId)
                .orElseThrow(() -> new IllegalArgumentException("base node not found: " + baseNodeId));
        NodeAtomVersion ver = versionRepo.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("version not found: " + versionId));
        node.setCurrentVersion(ver);
        baseNodeRepo.save(node);
    }

    // Base 수정 페이로드를 담는 단순 컨테이너
    public record BaseEditPayload(
            NodeCategory category,
            String situation,
            String decision,
            String optionsJson,
            String description,
            String contentHash
    ) {}
}
