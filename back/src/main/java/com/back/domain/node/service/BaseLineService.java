/**
 * BaseLineService
 * - 베이스라인 일괄 생성, 피벗 목록 반환
 * - 입력 검증/제목 생성 등 공통 로직은 NodeDomainSupport에 위임
 */
package com.back.domain.node.service;

import com.back.domain.node.dto.base.BaseLineBulkCreateRequest;
import com.back.domain.node.dto.base.BaseLineBulkCreateResponse;
import com.back.domain.node.dto.PivotListDto;
import com.back.domain.node.entity.BaseLine;
import com.back.domain.node.entity.BaseNode;
import com.back.domain.node.mapper.NodeMappers;
import com.back.domain.node.repository.BaseLineRepository;
import com.back.domain.node.repository.BaseNodeRepository;
import com.back.domain.user.entity.Role;
import com.back.domain.user.entity.User;
import com.back.domain.user.repository.UserRepository;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
class BaseLineService {

    private final BaseLineRepository baseLineRepository;
    private final BaseNodeRepository baseNodeRepository;
    private final UserRepository userRepository;
    private final NodeDomainSupport support;

    // 노드 일괄 생성(save chain)
    public BaseLineBulkCreateResponse createBaseLineWithNodes(BaseLineBulkCreateRequest request) {

        support.validateBulkRequest(request);
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND, "User not found: " + request.userId()));

        // Guest는 베이스라인 1개 제한
        if (user.getRole() == Role.GUEST && baseLineRepository.existsByUser_id(user.getId())) {
            throw new ApiException(ErrorCode.GUEST_BASELINE_LIMIT, "Guest user can have only one baseline.");
        }
        String title = support.normalizeOrAutoTitle(request.title(), user);

        BaseLine baseLine = baseLineRepository.save(BaseLine.builder().user(user).title(title).build());

        List<BaseLineBulkCreateRequest.BaseNodePayload> normalized = support.normalizeWithEnds(request.nodes());
        log.debug("[BL] normalized size = {}", normalized.size());


        BaseNode prev = null;
        List<BaseLineBulkCreateResponse.CreatedNode> created = new ArrayList<>();
        for (int i = 0; i < normalized.size(); i++) {
            BaseLineBulkCreateRequest.BaseNodePayload payload = normalized.get(i);
            BaseNode entity = new NodeMappers.BaseNodeCtxMapper(user, baseLine, prev).toEntity(payload);
            entity.guardBaseOptionsValid();
            BaseNode saved = baseNodeRepository.save(entity);
            created.add(new BaseLineBulkCreateResponse.CreatedNode(i, saved.getId()));
            prev = saved;
        }
        return new BaseLineBulkCreateResponse(baseLine.getId(), created);
    }

    // 가장 중요한: 피벗 목록 조회(헤더/꼬리 제외 + 중복 나이 제거 + 오름차순)
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
}
