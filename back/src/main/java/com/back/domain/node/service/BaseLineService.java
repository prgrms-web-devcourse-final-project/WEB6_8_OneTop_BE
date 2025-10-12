/**
 * BaseLineService (근본 개선판)
 * - 베이스라인 일괄 생성 시: NodeAtom/Version 생성 → BaseNode.currentVersion 연결
 * - 이어서 기본 브랜치(main)/루트 커밋(init) 생성 → 각 ageYear에 대한 초기 BaselinePatch 기록
 * - 이후 FOLLOW/PINNED 해석 시 체인에서 항상 초기 스냅샷을 찾을 수 있도록 보장
 */
package com.back.domain.node.service;

import com.back.domain.node.dto.PivotListDto;
import com.back.domain.node.dto.base.BaseLineBulkCreateRequest;
import com.back.domain.node.dto.base.BaseLineBulkCreateResponse;
import com.back.domain.node.entity.*;
import com.back.domain.node.mapper.NodeMappers;
import com.back.domain.node.repository.*;
import com.back.domain.scenario.repository.ScenarioRepository;
import com.back.domain.scenario.repository.SceneCompareRepository;
import com.back.domain.scenario.repository.SceneTypeRepository;
import com.back.domain.user.entity.Role;
import com.back.domain.user.entity.User;
import com.back.domain.user.repository.UserRepository;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
class BaseLineService {

    private final BaseLineRepository baseLineRepository;
    private final BaseNodeRepository baseNodeRepository;
    private final DecisionLineRepository decisionLineRepository;
    private final DecisionNodeRepository decisionNodeRepository;
    private final UserRepository userRepository;
    private final NodeDomainSupport support;
    private final ScenarioRepository scenarioRepository;
    private final SceneTypeRepository sceneTypeRepository;
    private final SceneCompareRepository sceneCompareRepository;

    // 하이브리드 초기화용
    private final NodeAtomRepository atomRepo;
    private final NodeAtomVersionRepository versionRepo;
    private final BaselineBranchRepository branchRepo;
    private final BaselineCommitRepository commitRepo;
    private final BaselinePatchRepository patchRepo;
    private final EntityManager em;

    private final NodeMappers mappers;

    // 가장 중요한: 라인 생성 + 루트 커밋 + 초기 패치
    @Transactional
    public BaseLineBulkCreateResponse createBaseLineWithNodes(BaseLineBulkCreateRequest request) {

        support.validateBulkRequest(request);
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND, "User not found: " + request.userId()));

        if (user.getRole() == Role.GUEST && baseLineRepository.existsByUser_Id(user.getId())) {
            throw new ApiException(ErrorCode.GUEST_BASELINE_LIMIT, "Guest user can have only one baseline.");
        }
        String title = support.normalizeOrAutoTitle(request.title(), user);

        BaseLine baseLine = baseLineRepository.save(BaseLine.builder().user(user).title(title).build());

        List<BaseLineBulkCreateRequest.BaseNodePayload> normalized = support.normalizeWithEnds(request.nodes());
        log.debug("[BL] normalized size = {}", normalized.size());

        BaseNode prev = null;
        List<BaseLineBulkCreateResponse.CreatedNode> created = new ArrayList<>();
        List<BaseNode> createdEntities = new ArrayList<>(normalized.size());

        for (int i = 0; i < normalized.size(); i++) {
            BaseLineBulkCreateRequest.BaseNodePayload payload = normalized.get(i);

            BaseNode entity = mappers.new BaseNodeCtxMapper(user, baseLine, prev).toEntity(payload);
            entity.guardBaseOptionsValid();

            // NodeAtom/Version 생성 후 BaseNode.currentVersion 연결
            NodeAtom atom = atomRepo.save(NodeAtom.builder().contentKey(null).build());
            NodeAtomVersion ver = versionRepo.save(NodeAtomVersion.builder()
                    .atom(atom)
                    .parentVersion(null)
                    .category(payload.category())
                    .situation(payload.situation())
                    .decision(payload.decision())
                    .optionsJson(null)
                    .description(payload.description())
                    .ageYear(payload.ageYear())
                    .contentHash(null)
                    .build());
            entity.setCurrentVersion(ver);

            BaseNode saved = baseNodeRepository.save(entity);
            created.add(new BaseLineBulkCreateResponse.CreatedNode(i, saved.getId()));
            createdEntities.add(saved);
            prev = saved;
        }

        // 기본 브랜치(main)와 루트 커밋 생성
        BaselineBranch main = branchRepo.save(BaselineBranch.builder()
                .baseLine(baseLine)
                .name("main")
                .headCommit(null)
                .build());
        BaselineCommit root = commitRepo.save(BaselineCommit.newCommit(main, null, user.getId(), "init"));
        main.moveHeadTo(root);
        branchRepo.save(main);

        // 가장 많이 사용하는: 생성된 노드들에 대한 초기 패치 저장
        for (BaseNode bn : createdEntities) {
            NodeAtomVersion v = bn.getCurrentVersion();
            if (v == null) continue;
            patchRepo.save(BaselinePatch.of(root, bn.getAgeYear(), null, v));
        }

        return new BaseLineBulkCreateResponse(baseLine.getId(), created);
    }

    // 헤더/꼬리 제외 pivot 목록 반환
    @Transactional(readOnly = true)
    public PivotListDto getPivotBaseNodes(Long baseLineId) {
        List<BaseNode> ordered = support.getOrderedBaseNodes(baseLineId);
        if (ordered.size() <= 2) return new PivotListDto(baseLineId, List.of());

        Map<Integer, BaseNode> uniqByAge = new LinkedHashMap<>();
        for (int i = 1; i < ordered.size() - 1; i++) {
            BaseNode n = ordered.get(i);
            uniqByAge.putIfAbsent(n.getAgeYear(), n);
        }

        List<PivotListDto.PivotDto> list = new ArrayList<>();
        int idx = 0;
        for (BaseNode n : uniqByAge.values()) {
            list.add(new PivotListDto.PivotDto(idx++, n.getId(), n.getCategory(), n.getSituation(), n.getAgeYear(), n.getDescription()));
        }
        return new PivotListDto(baseLineId, list);
    }

    @Transactional
    public void deleteBaseLineDeep(Long userId, Long baseLineId) {
        // 가장 많이 사용하는 호출: 소유자/존재 여부 검증
        boolean owned = baseLineRepository.existsByIdAndUser_Id(baseLineId, userId);
        if (!owned) throw new ApiException(ErrorCode.BASE_LINE_NOT_FOUND, "baseline not found or not owned");

        // 가장 많이 사용하는 호출: 결정노드 → 결정라인
        decisionNodeRepository.deleteByDecisionLine_BaseLine_Id(baseLineId);
        decisionLineRepository.deleteByBaseLine_Id(baseLineId);

        // 가장 많이 사용하는 호출: DVCS 역순(Patch→Commit→Branch)
        patchRepo.deleteByCommit_Branch_BaseLine_Id(baseLineId);
        branchRepo.clearHeadByBaseLineId(baseLineId);
        commitRepo.deleteByBranch_BaseLine_Id(baseLineId);
        branchRepo.deleteByBaseLine_Id(baseLineId);

        // (중간 플러시) 위 벌크 삭제 쿼리 확실히 반영
        em.flush();
        em.clear();

        // 시나리오 삭제(자식 → 부모 순서)
        List<Long> scenarioIds = scenarioRepository.findIdsByBaseLine_Id(baseLineId);
        if (scenarioIds != null && !scenarioIds.isEmpty()) {
            // 가장 많이 사용하는 호출: 시나리오 비교/타입(자식 테이블) 선삭제
            sceneCompareRepository.deleteByScenario_IdIn(scenarioIds);
            sceneTypeRepository.deleteByScenario_IdIn(scenarioIds);

            // (중간 플러시) 자식이 먼저 사라진 것을 보장
            em.flush();
            em.clear();

            // 부모(Scenario) 벌크 삭제
            scenarioRepository.deleteByIdIn(scenarioIds);
        }

        // (중간 플러시) 시나리오 트리 정리 완료 보장
        em.flush();
        em.clear();

        // 가장 많이 사용하는 호출: 베이스노드 → 베이스라인
        baseNodeRepository.deleteByBaseLine_Id(baseLineId);
        baseLineRepository.deleteByIdAndUser_Id(baseLineId, userId);
    }
}
