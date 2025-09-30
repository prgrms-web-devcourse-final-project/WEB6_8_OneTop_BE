/**
 * NodeMappers
 * - BaseNodeCtxMapper(쓰기, 컨텍스트 보유) : BaseNodeCreateRequestDto → BaseNode, BaseNode → BaseLineDto
 * - DecisionNodeCtxMapper(쓰기, 컨텍스트 보유): DecisionNodeCreateRequestDto → DecisionNode, DecisionNode → DecLineDto
 * - BASE_READ / DECISION_READ(읽기, 전역 함수형 매퍼): Entity → DTO
 */
package com.back.domain.node.mapper;

import com.back.domain.node.dto.base.BaseLineBulkCreateRequest;
import com.back.domain.node.dto.base.BaseLineDto;
import com.back.domain.node.dto.base.BaseNodeCreateRequestDto;
import com.back.domain.node.dto.base.BaseNodeDto;
import com.back.domain.node.dto.decision.DecNodeDto;
import com.back.domain.node.dto.decision.DecisionNodeCreateRequestDto;
import com.back.domain.node.entity.*;
import com.back.domain.user.entity.User;
import com.back.global.mapper.Mapper;
import com.back.global.mapper.MappingException;
import com.back.global.mapper.TwoWayMapper;

import java.util.ArrayList;
import java.util.List;

public final class NodeMappers {

    private NodeMappers() {}

    // BaseLine -> BaseLineDto
    public static final Mapper<BaseLine, BaseLineDto> BASELINE_READ = e -> {
        if (e == null) throw new MappingException("BaseLine is null");
        return new BaseLineDto(
                e.getId(),
                e.getTitle()
        );
    };


    // BaseNode -> BaseLineDto
    public static final Mapper<BaseNode, BaseNodeDto> BASE_READ = e -> {
        if (e == null) throw new MappingException("BaseNode is null");
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
                e.getDescription()
        );
    };

    // DecisionNode -> DecLineDto
    public static final Mapper<DecisionNode, DecNodeDto> DECISION_READ = e -> {
        if (e == null) throw new MappingException("DecisionNode is null");
        List<String> opts = new ArrayList<>(3);
        if (e.getOption1() != null) opts.add(e.getOption1());
        if (e.getOption2() != null) opts.add(e.getOption2());
        if (e.getOption3() != null) opts.add(e.getOption3());
        return new DecNodeDto(
                e.getId(),
                e.getUser() != null ? e.getUser().getId() : null,
                e.getNodeKind() != null ? e.getNodeKind().name() : NodeType.DECISION.name(),
                e.getCategory(),
                e.getSituation(),
                e.getDecision(),
                e.getAgeYear(),
                e.getDecisionLine() != null ? e.getDecisionLine().getId() : null,
                e.getParent() != null ? e.getParent().getId() : null,
                e.getBaseNode() != null ? e.getBaseNode().getId() : null,
                e.getBackground(),
                opts.isEmpty() ? null : List.copyOf(opts),
                e.getSelectedIndex(),
                e.getParentOptionIndex(),
                e.getDescription()
        );
    };

    public static final class BaseNodeCtxMapper implements TwoWayMapper<BaseNodeCreateRequestDto, BaseNode, BaseNodeDto> {
        private final User user;
        private final BaseLine baseLine;
        private final BaseNode parent;

        public BaseNodeCtxMapper(User user, BaseLine baseLine, BaseNode parent) {
            this.user = user; this.baseLine = baseLine; this.parent = parent;
        }

        // BaseNode 단건 생성
        @Override
        public BaseNode toEntity(BaseNodeCreateRequestDto req) {
            if (req == null) throw new MappingException("BaseNodeCreateRequestDto is null");
            BaseNode entity = BaseNode.builder()
                    .user(user).baseLine(baseLine).parent(parent)
                    .nodeKind(req.nodeKind() != null ? req.nodeKind() : NodeType.BASE)
                    .category(req.category()).situation(req.situation())
                    .ageYear(req.ageYear() != null ? req.ageYear() : 0)
                    .build();
            return entity;
        }

        // BaseLine 일괄 생성: Payload → BaseNode (decision→fixedChoice로 흡수)
        public BaseNode toEntity(BaseLineBulkCreateRequest.BaseNodePayload p) {
            if (p == null) throw new MappingException("BaseLineBulkCreateRequest.BaseNodePayload is null");
            BaseNode entity = BaseNode.builder()
                    .user(user).baseLine(baseLine).parent(parent)
                    .nodeKind(NodeType.BASE)
                    .category(p.category()).situation(p.situation()).decision(p.decision())
                    .ageYear(p.ageYear() != null ? p.ageYear() : 0)
                    .fixedChoice(p.decision()) // 고정 선택은 기존 decision을 재사용
                    .build();
            return entity;
        }

        // BaseNode -> BaseLineDto
        @Override
        public BaseNodeDto toResponse(BaseNode entity) {
            return BASE_READ.map(entity);
        }
    }

    public static final class DecisionNodeCtxMapper implements TwoWayMapper<DecisionNodeCreateRequestDto, DecisionNode, DecNodeDto> {
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

        // DecisionNode 생성
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
                d.setOption1(null);
                d.setOption2(null);
                d.setOption3(null);
                d.setSelectedIndex(null);
            } else {
                final int size = Math.min(3, opts.size());
                final String o1 = (size >= 1 && opts.get(0) != null && !opts.get(0).isBlank()) ? opts.get(0).trim() : null;
                final String o2 = (size >= 2 && opts.get(1) != null && !opts.get(1).isBlank()) ? opts.get(1).trim() : null;
                final String o3 = (size >= 3 && opts.get(2) != null && !opts.get(2).isBlank()) ? opts.get(2).trim() : null;

                d.setOption1(o1);
                d.setOption2(o2);
                d.setOption3(o3);

                Integer sel = req.selectedIndex();
                if (sel == null && size == 1) sel = 0;
                if (sel != null && (sel < 0 || sel >= size)) sel = null;
                d.setSelectedIndex(sel);

                if ((d.getDecision() == null || d.getDecision().isBlank()) && sel != null) {
                    switch (sel) {
                        case 0 -> d.setDecision(o1);
                        case 1 -> d.setDecision(o2);
                        case 2 -> d.setDecision(o3);
                    }
                }
            }

            return d;
        }

        // DecisionNode -> DecLineDto
        @Override
        public DecNodeDto toResponse(DecisionNode entity) {
            return DECISION_READ.map(entity);
        }
    }
}
