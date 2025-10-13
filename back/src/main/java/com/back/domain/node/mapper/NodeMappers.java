/**
 * NodeMappers (하이브리드 대응 버전)
 * - 생성자 주입 완료 후 람다 매퍼(BASELINE_READ/BASE_READ/DECISION_READ)를 초기화해 주입 필드 참조 시점 문제를 제거
 * - READ 경로: Base/Decision 엔티티를 DTO로 변환할 때 버전 해석(VersionResolver + NodeAtomVersionRepository) 값을 effective*로 주입
 * - WRITE 경로: 내부 컨텍스트 매퍼(BaseNodeCtxMapper/DecisionNodeCtxMapper)가 DTO→엔티티 변환을 담당
 * - 옵션 JSON <-> List 변환은 ObjectMapper로 처리
 */
package com.back.domain.node.mapper;

import com.back.domain.node.dto.base.BaseLineBulkCreateRequest;
import com.back.domain.node.dto.base.BaseLineDto;
import com.back.domain.node.dto.base.BaseNodeCreateRequestDto;
import com.back.domain.node.dto.base.BaseNodeDto;
import com.back.domain.node.dto.decision.DecNodeDto;
import com.back.domain.node.dto.decision.DecisionNodeCreateRequestDto;
import com.back.domain.node.entity.*;
import com.back.domain.node.repository.NodeAtomVersionRepository;
import com.back.domain.node.service.VersionResolver;
import com.back.domain.user.entity.User;
import com.back.global.mapper.Mapper;
import com.back.global.mapper.MappingException;
import com.back.global.mapper.TwoWayMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public final class NodeMappers {

    private final VersionResolver resolver;
    private final NodeAtomVersionRepository versionRepo;
    private final ObjectMapper om;

    public final Mapper<BaseLine, BaseLineDto> BASELINE_READ;
    public final Mapper<BaseNode, BaseNodeDto> BASE_READ;
    public final Mapper<DecisionNode, DecNodeDto> DECISION_READ;

    public NodeMappers(VersionResolver resolver,
                       NodeAtomVersionRepository versionRepo,
                       ObjectMapper objectMapper) {
        this.resolver = resolver;
        this.versionRepo = versionRepo;
        this.om = (objectMapper != null) ? objectMapper : new ObjectMapper();

        // 베이스라인을 DTO로 변환
        this.BASELINE_READ = e -> {
            if (e == null) throw new MappingException("BaseLine is null");
            return new BaseLineDto(e.getId(), e.getTitle());
        };

        // 베이스 노드를 DTO로 변환(버전 해석 포함)
        this.BASE_READ = e -> {
            if (e == null) throw new MappingException("BaseNode is null");

            Long currentVersionId = e.getCurrentVersion() != null ? e.getCurrentVersion().getId() : null;
            NodeAtomVersion ver = (currentVersionId != null)
                    ? versionRepo.findById(currentVersionId).orElse(null)
                    : null;

            return new BaseNodeDto(
                    e.getId(),
                    e.getUser() != null ? e.getUser().getId() : null,
                    e.getNodeKind() != null ? e.getNodeKind().name() : NodeType.BASE.name(),
                    e.getCategory(),
                    e.getSituation(),
                    e.getDecision(),
                    e.getAgeYear(),
                    e.getBaseLine() != null ? e.getBaseLine().getId() : null,
                    e.getParent() != null ? e.getParent().getId() : null,
                    e.getBaseLine() != null ? e.getBaseLine().getTitle() : null,
                    e.getFixedChoice(),
                    e.getAltOpt1(),
                    e.getAltOpt2(),
                    e.getAltOpt1TargetDecisionId(),
                    e.getAltOpt2TargetDecisionId(),
                    e.getDescription(),
                    currentVersionId,
                    ver != null ? ver.getCategory() : null,
                    ver != null ? ver.getSituation() : null,
                    ver != null ? ver.getDecision() : null,
                    ver != null ? parseOptionsJson(ver.getOptionsJson()) : null,
                    ver != null ? ver.getDescription() : null
            );
        };

        // 결정 노드를 DTO로 변환(버전 해석 포함)
        this.DECISION_READ = e -> {
            if (e == null) throw new MappingException("DecisionNode is null");

            DecisionLine line = e.getDecisionLine();
            Long baseLineId = line != null && line.getBaseLine() != null ? line.getBaseLine().getId() : null;
            Long baseBranchId = line != null && line.getBaseBranch() != null ? line.getBaseBranch().getId() : null;
            Long pinnedCommitId = line != null && line.getPinnedCommit() != null ? line.getPinnedCommit().getId() : null;

            Long overrideVersionId = e.getOverrideVersion() != null ? e.getOverrideVersion().getId() : null;
            Long effectiveVersionId = resolver.resolveVersionId(
                    baseLineId,
                    baseBranchId,
                    pinnedCommitId,
                    e.getFollowPolicy() != null ? e.getFollowPolicy() : FollowPolicy.FOLLOW,
                    e.getAgeYear(),
                    overrideVersionId
            );

            NodeAtomVersion ver = (effectiveVersionId != null)
                    ? versionRepo.findById(effectiveVersionId).orElse(null)
                    : null;

            List<String> fallbackOpts = toListOptions(e);
            List<String> effectiveOpts = ver != null ? parseOptionsJson(ver.getOptionsJson()) : null;

            return new DecNodeDto(
                    e.getId(),
                    e.getUser() != null ? e.getUser().getId() : null,
                    e.getNodeKind() != null ? e.getNodeKind().name() : NodeType.DECISION.name(),
                    e.getCategory(),
                    e.getSituation(),
                    e.getDecision(),
                    e.getAgeYear(),
                    line != null ? line.getId() : null,
                    e.getParent() != null ? e.getParent().getId() : null,
                    e.getBaseNode() != null ? e.getBaseNode().getId() : null,
                    e.getBackground(),
                    fallbackOpts,
                    e.getSelectedIndex(),
                    e.getParentOptionIndex(),
                    e.getDescription(),
                    e.getAiNextSituation(),
                    e.getAiNextRecommendedOption(),
                    e.getFollowPolicy(),
                    pinnedCommitId,
                    null, // virtual 투영은 상위 레이어에서 주입
                    ver != null ? ver.getCategory() : null,
                    ver != null ? ver.getSituation() : null,
                    ver != null ? ver.getDecision() : null,
                    effectiveOpts,
                    ver != null ? ver.getDescription() : null
            );
        };
    }

    // 엔티티 옵션 필드를 List로 정규화
    private List<String> toListOptions(DecisionNode e) {
        List<String> opts = new ArrayList<>(3);
        if (e.getOption1() != null && !e.getOption1().isBlank()) opts.add(e.getOption1());
        if (e.getOption2() != null && !e.getOption2().isBlank()) opts.add(e.getOption2());
        if (e.getOption3() != null && !e.getOption3().isBlank()) opts.add(e.getOption3());
        return opts.isEmpty() ? null : List.copyOf(opts);
    }

    // NodeAtomVersion.optionsJson을 List<String>으로 변환
    private List<String> parseOptionsJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            List<String> list = om.readValue(json, new TypeReference<List<String>>() {});
            List<String> trimmed = new ArrayList<>();
            for (String s : list) if (s != null && !s.isBlank()) trimmed.add(s.trim());
            return trimmed.isEmpty() ? null : List.copyOf(trimmed);
        } catch (Exception ignore) {
            return null;
        }
    }

    public final class BaseNodeCtxMapper implements TwoWayMapper<BaseNodeCreateRequestDto, BaseNode, BaseNodeDto> {
        private final User user;
        private final BaseLine baseLine;
        private final BaseNode parent;

        public BaseNodeCtxMapper(User user, BaseLine baseLine, BaseNode parent) {
            this.user = user; this.baseLine = baseLine; this.parent = parent;
        }

        // BaseNode 생성 요청을 엔티티로 변환
        @Override
        public BaseNode toEntity(BaseNodeCreateRequestDto req) {
            if (req == null) throw new MappingException("BaseNodeCreateRequestDto is null");
            return BaseNode.builder()
                    .user(user).baseLine(baseLine).parent(parent)
                    .nodeKind(req.nodeKind() != null ? req.nodeKind() : NodeType.BASE)
                    .category(req.category()).situation(req.situation())
                    .ageYear(req.ageYear() != null ? req.ageYear() : 0)
                    .description(req.description())
                    .build();
        }

        // 일괄 생성 페이로드를 엔티티로 변환
        public BaseNode toEntity(BaseLineBulkCreateRequest.BaseNodePayload p) {
            if (p == null) throw new MappingException("BaseLineBulkCreateRequest.BaseNodePayload is null");
            return BaseNode.builder()
                    .user(user).baseLine(baseLine).parent(parent)
                    .nodeKind(NodeType.BASE)
                    .category(p.category()).situation(p.situation()).decision(p.decision())
                    .ageYear(p.ageYear() != null ? p.ageYear() : 0)
                    .fixedChoice(p.decision())
                    .description(p.description())
                    .build();
        }

        // BaseNode 엔티티를 DTO로 변환
        @Override
        public BaseNodeDto toResponse(BaseNode entity) {
            return BASE_READ.map(entity);
        }
    }

    public final class DecisionNodeCtxMapper implements TwoWayMapper<DecisionNodeCreateRequestDto, DecisionNode, DecNodeDto> {
        private final User user;
        private final DecisionLine decisionLine;
        private final DecisionNode parentDecision;
        private final BaseNode baseNode;
        private final String background;

        public DecisionNodeCtxMapper(User user, DecisionLine decisionLine, DecisionNode parentDecision,
                                     BaseNode baseNode, String background) {
            this.user = user; this.decisionLine = decisionLine; this.parentDecision = parentDecision;
            this.baseNode = baseNode; this.background = background;
        }

        // DecisionNode 생성 요청을 엔티티로 변환
        @Override
        public DecisionNode toEntity(DecisionNodeCreateRequestDto req) {
            if (req == null) throw new MappingException("DecisionNodeCreateRequestDto is null");
            DecisionNode d = DecisionNode.builder()
                    .user(user).nodeKind(NodeType.DECISION)
                    .decisionLine(decisionLine).parent(parentDecision).baseNode(baseNode)
                    .category(req.category()).situation(req.situation()).decision(req.decision())
                    .ageYear(req.ageYear() != null ? req.ageYear() : 0)
                    .background(background)
                    .parentOptionIndex(req.parentOptionIndex())
                    .build();

            List<String> opts = req.options();
            if (opts == null || opts.isEmpty()) {
                d.setOption1(null); d.setOption2(null); d.setOption3(null);
                d.setSelectedIndex(null);
            } else {
                final int size = Math.min(3, opts.size());
                final String o1 = (size >= 1 && notBlank(opts.get(0))) ? opts.get(0).trim() : null;
                final String o2 = (size >= 2 && notBlank(opts.get(1))) ? opts.get(1).trim() : null;
                final String o3 = (size >= 3 && notBlank(opts.get(2))) ? opts.get(2).trim() : null;

                d.setOption1(o1); d.setOption2(o2); d.setOption3(o3);

                Integer sel = req.selectedIndex();
                if (sel == null && size == 1) sel = 0;
                if (sel != null && (sel < 0 || sel >= size)) sel = null;
                d.setSelectedIndex(sel);

                if ((d.getDecision() == null || d.getDecision().isBlank()) && sel != null) {
                    if (sel == 0) d.setDecision(o1);
                    else if (sel == 1) d.setDecision(o2);
                    else if (sel == 2) d.setDecision(o3);
                }
            }
            return d;
        }

        // DecisionNode 엔티티를 DTO로 변환
        @Override
        public DecNodeDto toResponse(DecisionNode entity) {
            return DECISION_READ.map(entity);
        }

        private boolean notBlank(String s) { return s != null && !s.isBlank(); }
    }
}
