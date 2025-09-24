/**
 * NodeDomainSupport (공통 헬퍼)
 * - 입력 검증, 피벗/나이 해석, 정렬 조회, 옵션 검증, 제목 생성, 예외 매핑 등
 */
package com.back.domain.node.service;

import com.back.domain.node.dto.BaseLineBulkCreateRequest;
import com.back.domain.node.entity.*;
import com.back.domain.node.repository.BaseLineRepository;
import com.back.domain.node.repository.BaseNodeRepository;
import com.back.domain.node.repository.DecisionLineRepository;
import com.back.domain.user.entity.User;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
class NodeDomainSupport {

    private static final int MAX_SITUATION_LEN = 1000;
    private static final int MAX_TITLE_LEN = 100;
    private static final String UNTITLED_PREFIX = "제목없음";

    private final BaseLineRepository baseLineRepository;
    private final BaseNodeRepository baseNodeRepository;
    private final DecisionLineRepository decisionLineRepository;

    // BaseLine 존재 보장
    public void ensureBaseLineExists(Long baseLineId) {
        baseLineRepository.findById(baseLineId)
                .orElseThrow(() -> new ApiException(ErrorCode.BASE_LINE_NOT_FOUND, "BaseLine not found: " + baseLineId));
    }

    // BaseLine 정렬된 노드 조회 + 라인 존재 확인
    public List<BaseNode> getOrderedBaseNodes(Long baseLineId) {
        BaseLine baseLine = baseLineRepository.findById(baseLineId)
                .orElseThrow(() -> new ApiException(ErrorCode.BASE_LINE_NOT_FOUND, "BaseLine not found: " + baseLineId));
        List<BaseNode> nodes = baseNodeRepository.findByBaseLine_IdOrderByAgeYearAscIdAsc(baseLine.getId());
        return nodes == null ? List.of() : nodes;
    }

    // 허용 피벗 나이 목록(헤더/꼬리 제외, distinct asc)
    public List<Integer> allowedPivotAges(List<BaseNode> ordered) {
        if (ordered.size() <= 2) return List.of();
        LinkedHashSet<Integer> set = new LinkedHashSet<>();
        for (int i = 1; i < ordered.size() - 1; i++) set.add(ordered.get(i).getAgeYear());
        List<Integer> ages = new ArrayList<>(set);
        ages.sort(Comparator.naturalOrder());
        return ages;
    }

    // 피벗 나이 해석(순번/나이)
    public int resolvePivotAge(Integer pivotOrd, Integer pivotAge, List<Integer> pivotAges) {
        if (pivotAge != null) {
            if (!pivotAges.contains(pivotAge)) throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "invalid pivotAge");
            return pivotAge;
        }
        if (pivotOrd == null || pivotOrd < 0 || pivotOrd >= pivotAges.size())
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "invalid pivotOrd");
        return pivotAges.get(pivotOrd);
    }

    // 다음 나이 해석(명시 없으면 부모보다 큰 첫 피벗)
    public int resolveNextAge(Integer requested, int parentAge, List<Integer> pivotAges) {
        if (requested != null) {
            if (!pivotAges.contains(requested) || requested <= parentAge)
                throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "invalid next age");
            return requested;
        }
        return pivotAges.stream().filter(a -> a > parentAge)
                .findFirst().orElseThrow(() -> new ApiException(ErrorCode.INVALID_INPUT_VALUE, "no more pivots"));
    }

    // 피벗 나이로 BaseNode 찾기
    public BaseNode findBaseNodeByAge(List<BaseNode> ordered, int age) {
        for (BaseNode b : ordered) if (b.getAgeYear() == age) return b;
        throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "pivot base node not found for age " + age);
    }

    // 같은 라인에서 해당 나이 중복 방지
    public void ensureAgeVacant(DecisionLine line, int ageYear) {
        for (DecisionNode d : line.getDecisionNodes()) if (d.getAgeYear() == ageYear)
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "decision already exists at this age");
    }

    // 베이스 분기 슬롯 인덱스 검증
    public int requireAltIndex(Integer idx) {
        if (idx == null || (idx != 0 && idx != 1))
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "selectedAltIndex must be 0 or 1");
        return idx;
    }

    // 옵션 1~3, selectedIndex/decision 일관성 검증
    public void validateOptions(List<String> options, Integer selectedIndex, String decision) {
        if (options == null || options.isEmpty()) throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "options required");
        if (options.size() > 3) throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "options up to 3");
        for (String s : options) if (s == null || s.isBlank()) throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "option text blank");
        if (selectedIndex != null && (selectedIndex < 0 || selectedIndex >= options.size()))
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "selectedIndex out of range");
        if (decision != null && selectedIndex != null) {
            if (!Objects.equals(decision, options.get(selectedIndex)))
                throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "decision must equal options[selectedIndex]");
        }
    }

    // 피벗 alt 슬롯 텍스트 채우기/검증
    public void ensurePivotAltTexts(BaseNode pivot, List<String> options) {
        String o1 = options.size() > 0 ? options.get(0) : null;
        String o2 = options.size() > 1 ? options.get(1) : null;

        if (o1 != null && !o1.isBlank()) {
            if (pivot.getAltOpt1() == null || pivot.getAltOpt1().isBlank()) {
                pivot.setAltOpt1(o1);
            } else if (!pivot.getAltOpt1().equals(o1)) {
                throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "altOpt1 text mismatch");
            }
        }
        if (o2 != null && !o2.isBlank()) {
            if (pivot.getAltOpt2() == null || pivot.getAltOpt2().isBlank()) {
                pivot.setAltOpt2(o2);
            } else if (!pivot.getAltOpt2().equals(o2)) {
                throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "altOpt2 text mismatch");
            }
        }
        if (pivot.getAltOpt1TargetDecisionId() != null && o1 != null && !o1.equals(pivot.getAltOpt1())) {
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "altOpt1 already linked");
        }
        if (pivot.getAltOpt2TargetDecisionId() != null && o2 != null && !o2.equals(pivot.getAltOpt2())) {
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "altOpt2 already linked");
        }
    }

    // 기본 제목 생성: “제목없음{n}” 자동증가
    public String normalizeOrAutoTitle(String raw, User user) {
        String t = (raw == null || raw.trim().isEmpty()) ? null : raw.trim();
        if (t == null) {
            long seq = Math.max(1, baseLineRepository.countByUser(user) + 1);
            String candidate;
            do {
                candidate = UNTITLED_PREFIX + seq;
                seq++;
            } while (candidate.length() <= MAX_TITLE_LEN
                    && baseLineRepository.existsByUserAndTitle(user, candidate));
            t = candidate;
        }
        if (t.length() > MAX_TITLE_LEN) t = t.substring(0, MAX_TITLE_LEN);
        return t;
    }

    // 요청 전체 유효성 검증
    public void validateBulkRequest(BaseLineBulkCreateRequest request) {
        if (request == null) throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "request must not be null");
        if (request.userId() == null) throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "userId is required");
        if (request.nodes() == null || request.nodes().isEmpty())
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "nodes must not be empty");
        if (request.nodes().size() < 2)
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "nodes length must be >= 2 (header and tail required)");
        for (int i = 0; i < request.nodes().size(); i++) {
            BaseLineBulkCreateRequest.BaseNodePayload p = request.nodes().get(i);
            if (p.category() == null) throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "category is required at index=" + i);
            if (p.ageYear() == null || p.ageYear() < 0)
                throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "ageYear must be >= 0 at index=" + i);
            String s = Optional.ofNullable(p.situation()).orElse("");
            if (s.length() > MAX_SITUATION_LEN)
                throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "situation length exceeds " + MAX_SITUATION_LEN + " at index=" + i);
        }
    }

    // 도메인 런타임 예외 → ApiException
    public RuntimeException mapDomainToApi(RuntimeException e) {
        return new ApiException(ErrorCode.INVALID_INPUT_VALUE, e.getMessage());
    }

    // DecisionLine 필수 조회
    public DecisionLine requireDecisionLine(Long decisionLineId) {
        return decisionLineRepository.findById(decisionLineId)
                .orElseThrow(() -> new ApiException(ErrorCode.DECISION_LINE_NOT_FOUND, "DecisionLine not found: " + decisionLineId));
    }

    // 배경 생성 훅
    public String resolveBackground(String situation) {
        return situation == null ? "" : situation;
    }
}
