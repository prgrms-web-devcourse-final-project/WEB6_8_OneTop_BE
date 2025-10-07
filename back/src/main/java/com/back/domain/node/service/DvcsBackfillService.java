// DvcsBackfillService.java
package com.back.domain.node.service;

import com.back.domain.node.entity.*;
import com.back.domain.node.repository.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DvcsBackfillService {

    private final BaseLineRepository baseLineRepo;
    private final BaseNodeRepository baseNodeRepo;
    private final DecisionLineRepository decisionLineRepo;
    private final BaselineBranchRepository branchRepo;
    private final BaselineCommitRepository commitRepo;
    private final BaselinePatchRepository patchRepo;
    private final NodeAtomRepository atomRepo;
    private final NodeAtomVersionRepository versionRepo;
    private final EntityManager em; // 필요 시 초기화 용

    @Transactional
    public void backfill() {
        Map<Long, BaselineBranch> mainByBaseLine = new HashMap<>();

        // 1) 각 BaseLine에 main 브랜치/루트 커밋 보장
        for (BaseLine bl : baseLineRepo.findAll()) {
            BaselineBranch main = branchRepo.findByBaseLine_Id(bl.getId()).stream()
                    .filter(b -> "main".equals(b.getName()))
                    .findFirst()
                    .orElseGet(() -> branchRepo.save(BaselineBranch.builder()
                            .baseLine(bl).name("main").headCommit(null).build()));

            if (main.getHeadCommit() == null) {
                BaselineCommit root = commitRepo.save(
                        BaselineCommit.newCommit(main, null, bl.getUser().getId(), "init")
                );
                main.moveHeadTo(root);
                branchRepo.save(main);
                // 필요 시 초기화
                Hibernate.initialize(root.getParentCommit()); // null일 수 있지만 보호적 초기화
            }
            mainByBaseLine.put(bl.getId(), main);
        }

        // 2) BaseNode → NodeAtom/Version 생성 및 currentVersion 세팅
        List<BaseNode> nodes = baseNodeRepo.findAll();
        nodes.sort(Comparator.comparing(BaseNode::getId));
        for (BaseNode bn : nodes) {
            if (bn.getCurrentVersion() != null) continue;

            NodeAtom atom = atomRepo.save(NodeAtom.builder().contentKey(null).build());
            NodeAtomVersion ver = versionRepo.save(NodeAtomVersion.builder()
                    .atom(atom)
                    .parentVersion(null)
                    .category(bn.getCategory() != null ? bn.getCategory() : NodeCategory.ETC)
                    .situation(bn.getSituation())
                    .decision(bn.getDecision())
                    .optionsJson(null)
                    .description(bn.getDescription())
                    .ageYear(bn.getAgeYear())
                    .contentHash(hashOf(bn))
                    .build());
            bn.setCurrentVersion(ver);
            baseNodeRepo.save(bn);
        }

        // 3) 기존 DecisionLine에 baseBranch 연결
        for (DecisionLine dl : decisionLineRepo.findAll()) {
            if (dl.getBaseBranch() == null) {
                dl.setBaseBranch(mainByBaseLine.get(dl.getBaseLine().getId()));
                decisionLineRepo.save(dl);
            }
        }

        // 4) 각 BaseLine 체인에서 ageYear별 초기 패치가 없으면 루트 커밋에 생성
        for (BaseLine bl : baseLineRepo.findAll()) {
            BaselineBranch main = mainByBaseLine.get(bl.getId());
            if (main == null || main.getHeadCommit() == null) continue;

            // head → root 체인 수집 (TX 안이므로 LAZY 접근 OK)
            List<BaselineCommit> chain = new ArrayList<>();
            BaselineCommit cur = main.getHeadCommit();
            while (cur != null) {
                chain.add(cur);
                // LAZY 안전 접근
                Hibernate.initialize(cur.getParentCommit());
                cur = cur.getParentCommit();
            }
            if (chain.isEmpty()) continue;

            BaselineCommit root = chain.get(chain.size() - 1);
            List<Long> chainIds = chain.stream().map(BaselineCommit::getId).toList();

            List<BaseNode> ordered = baseNodeRepo.findByBaseLine_IdOrderByAgeYearAscIdAsc(bl.getId());
            for (BaseNode bn : ordered) {
                NodeAtomVersion v = bn.getCurrentVersion();
                if (v == null) continue;

                boolean exists = !patchRepo
                        .findByCommit_IdInAndAgeYearOrderByIdDesc(chainIds, bn.getAgeYear())
                        .isEmpty();
                if (!exists) {
                    patchRepo.save(BaselinePatch.of(root, bn.getAgeYear(), null, v));
                }
            }
        }
    }

    private String hashOf(BaseNode bn) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String[] parts = {
                    bn.getCategory() != null ? bn.getCategory().name() : "",
                    bn.getSituation(),
                    bn.getDecision(),
                    bn.getDescription(),
                    String.valueOf(bn.getAgeYear())
            };
            for (String p : parts) {
                if (p != null) md.update(p.getBytes(StandardCharsets.UTF_8));
                md.update((byte) 0x1F);
            }
            byte[] d = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return null; }
    }
}
