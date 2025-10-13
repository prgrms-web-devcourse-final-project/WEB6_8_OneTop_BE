/**
 * NodeDomainSupport (공통 헬퍼)
 * - 입력 검증, 피벗/나이 해석, 정렬 조회, 옵션 검증, 제목 생성, 예외 매핑 등
 * - FromBase 전용 옵션 규칙(1~2개, 단일 옵션은 선택 슬롯만) 포함
 */
package com.back.domain.node.service;

import com.back.domain.node.dto.base.BaseLineBulkCreateRequest;
import com.back.domain.node.entity.*;
import com.back.domain.node.repository.BaseLineRepository;
import com.back.domain.node.repository.BaseNodeRepository;
import com.back.domain.node.repository.DecisionLineRepository;
import com.back.domain.user.entity.User;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Component
@RequiredArgsConstructor
public class NodeDomainSupport {

    private static final int MAX_SITUATION_LEN = 1000;
    private static final int MAX_TITLE_LEN = 100;
    private static final String UNTITLED_PREFIX = "제목없음";

    private final BaseLineRepository baseLineRepository;
    private final BaseNodeRepository baseNodeRepository;
    private final DecisionLineRepository decisionLineRepository;

    // 프로세스 내 슬롯 잠금 (pivotId+slot 키에 대한 얇은 락)
    private static final ConcurrentHashMap<String, ReentrantLock> SLOT_LOCKS = new ConcurrentHashMap<>();


    // BaseLine 존재 보장
    public void ensureBaseLineExists(Long baseLineId) {
        baseLineRepository.findById(baseLineId)
                .orElseThrow(() -> new ApiException(ErrorCode.BASE_LINE_NOT_FOUND, "BaseLine not found: " + baseLineId));
    }

    // (가장 많이 사용하는) BaseLine 정렬된 노드 조회 + 라인 존재 확인
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
        if (ordered == null || ordered.size() <= 2) {
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "pivot base node not found for age " + age);
        }
        for (int i = 1; i < ordered.size() - 1; i++) {
            BaseNode b = ordered.get(i);
            if (b.getAgeYear() == age) return b;
        }
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

    // (가장 중요한) FromBase용 옵션 검증(1~2개, 선택 인덱스 범위만 체크)
    public void validateOptionsForFromBase(List<String> options, Integer selectedIndex) {
        if (options == null || options.isEmpty())
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "options required");
        if (options.size() > 2)
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "options up to 2 on from-base");
        for (String s : options)
            if (s == null || s.isBlank())
                throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "option text blank");
        if (selectedIndex != null && (selectedIndex < 0 || selectedIndex >= options.size()))
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "selectedIndex out of range");
    }

    // (가장 많이 사용하는) FromBase에서 피벗 슬롯 텍스트 반영(1~2개; 단일 옵션은 선택 슬롯에만 기록)
    public void upsertPivotAltTextsForFromBase(BaseNode pivot, List<String> options, int selectedAltIndex) {
        if (options == null || options.isEmpty()) return;

        // 단일 옵션: 선택 슬롯에만 적용
        if (options.size() == 1) {
            String text = options.get(0);
            if (selectedAltIndex == 0) {
                if (pivot.getAltOpt1TargetDecisionId() != null && !text.equals(pivot.getAltOpt1()))
                    throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "altOpt1 already linked");
                if (pivot.getAltOpt1() == null || pivot.getAltOpt1().isBlank()) pivot.setAltOpt1(text);
                else if (!pivot.getAltOpt1().equals(text)) throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "altOpt1 text mismatch");
            } else {
                if (pivot.getAltOpt2TargetDecisionId() != null && !text.equals(pivot.getAltOpt2()))
                    throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "altOpt2 already linked");
                if (pivot.getAltOpt2() == null || pivot.getAltOpt2().isBlank()) pivot.setAltOpt2(text);
                else if (!pivot.getAltOpt2().equals(text)) throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "altOpt2 text mismatch");
            }
            return;
        }

        // 옵션 2개: 앞 두 개를 alt1/alt2 반영(선택 슬롯 우선)
        String o1 = options.get(0);
        String o2 = options.get(1);

        if (selectedAltIndex == 0) {
            if (pivot.getAltOpt1TargetDecisionId() != null && !o1.equals(pivot.getAltOpt1()))
                throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "altOpt1 already linked");
            if (pivot.getAltOpt1() == null || pivot.getAltOpt1().isBlank()) pivot.setAltOpt1(o1);
            else if (!pivot.getAltOpt1().equals(o1)) throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "altOpt1 text mismatch");

            if (pivot.getAltOpt2() == null || pivot.getAltOpt2().isBlank()) pivot.setAltOpt2(o2);
            else if (!pivot.getAltOpt2().equals(o2)) throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "altOpt2 text mismatch");
        } else {
            if (pivot.getAltOpt2TargetDecisionId() != null && !o2.equals(pivot.getAltOpt2()))
                throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "altOpt2 already linked");
            if (pivot.getAltOpt2() == null || pivot.getAltOpt2().isBlank()) pivot.setAltOpt2(o2);
            else if (!pivot.getAltOpt2().equals(o2)) throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "altOpt2 text mismatch");

            if (pivot.getAltOpt1() == null || pivot.getAltOpt1().isBlank()) pivot.setAltOpt1(o1);
            else if (!pivot.getAltOpt1().equals(o1)) throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "altOpt1 text mismatch");
        }
    }

    // Next용 옵션 1~3, selectedIndex/decision 일관성 검증
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

        for (int i = 0; i < request.nodes().size(); i++) {
            BaseLineBulkCreateRequest.BaseNodePayload p = request.nodes().get(i);
            if (p.category() == null)
                throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "category is required at index=" + i);
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

    public void ensureOwner(Long requestUserId, Long resourceUserId) {
        if (requestUserId == null || !Objects.equals(requestUserId, resourceUserId)) {
            throw new ApiException(ErrorCode.HANDLE_ACCESS_DENIED, "owner mismatch");
        }
    }

    // BaseLine 기준으로 소유자 검증
    public void ensureOwnerOfBaseLine(Long requestUserId, BaseLine baseLine) {
        ensureOwner(requestUserId, baseLine.getUser().getId());
    }

    // DecisionLine 기준으로 소유자 검증
    public void ensureOwnerOfDecisionLine(Long requestUserId, DecisionLine line) {
        ensureOwner(requestUserId, line.getUser().getId());
    }

    // 엔티티 option1~3 -> List<String> 정규화
    public List<String> extractOptions(DecisionNode n) {
        List<String> ops = new ArrayList<>(3);
        if (n.getOption1() != null && !n.getOption1().isBlank()) ops.add(n.getOption1());
        if (n.getOption2() != null && !n.getOption2().isBlank()) ops.add(n.getOption2());
        if (n.getOption3() != null && !n.getOption3().isBlank()) ops.add(n.getOption3());
        return ops.isEmpty() ? null : ops;
    }

    // 피벗만 들어온 nodes를 헤더/테일 자동 부착하여 정규화
    public List<BaseLineBulkCreateRequest.BaseNodePayload> normalizeWithEnds(
            List<BaseLineBulkCreateRequest.BaseNodePayload> raw
    ) {
        if (raw == null || raw.isEmpty())
            throw new ApiException(ErrorCode.INVALID_INPUT_VALUE, "nodes must not be empty");

        // 정렬 보장(프론트 정렬 누락 대비)
        List<BaseLineBulkCreateRequest.BaseNodePayload> pivots = new ArrayList<>(raw);
        pivots.sort(Comparator.comparing(BaseLineBulkCreateRequest.BaseNodePayload::ageYear));

        // 이미 헤더/테일을 포함해 온 ‘레거시 입력’이라면 그대로 사용(중복 부착 방지)
        if (looksLikeLegacyWithEnds(pivots)) {
            return pivots;
        }

        // 헤더/테일 자동 생성 정책:
        // - 헤더: 첫 피벗 ageYear, situation/decision = "시작", category = 첫 피벗 category
        // - 테일: 마지막 피벗 ageYear, situation/decision = "결말", category = 마지막 피벗 category
        var first = pivots.get(0);
        var last  = pivots.get(pivots.size() - 1);

        var header = new BaseLineBulkCreateRequest.BaseNodePayload(
                first.category(),
                "시작",
                "시작",
                first.ageYear(),
                null
        );
        var tail = new BaseLineBulkCreateRequest.BaseNodePayload(
                last.category(),
                "결말",
                "결말",
                last.ageYear(),
                null
        );

        List<BaseLineBulkCreateRequest.BaseNodePayload> normalized = new ArrayList<>(pivots.size() + 2);
        normalized.add(header);
        normalized.addAll(pivots);
        normalized.add(tail);
        return normalized;
    }

    // 기존(헤더/테일 포함) 형태 감지 휴리스틱
    private boolean looksLikeLegacyWithEnds(List<BaseLineBulkCreateRequest.BaseNodePayload> nodes) {
        if (nodes.size() < 3) return false;
        String s0 = Optional.ofNullable(nodes.get(0).situation()).orElse("");
        String d0 = Optional.ofNullable(nodes.get(0).decision()).orElse("");
        String sZ = Optional.ofNullable(nodes.get(nodes.size() - 1).situation()).orElse("");
        String dZ = Optional.ofNullable(nodes.get(nodes.size() - 1).decision()).orElse("");
        if (s0.contains("시작") || d0.contains("시작") || sZ.contains("결말") || dZ.contains("결말")) return true;

        // 중간 구간의 최소/최대 ageYear가 양 끝과 동일하면 이미 가드 노드가 있을 확률 높음
        int minMid = nodes.get(1).ageYear();
        int maxMid = nodes.get(nodes.size() - 2).ageYear();
        return (minMid == nodes.get(0).ageYear()) || (maxMid == nodes.get(nodes.size() - 1).ageYear());
    }

    public <T> T withSlotLock(Long pivotId, int sel, Callable<T> body) {
        String key = pivotId + "#" + sel;
        ReentrantLock lock = SLOT_LOCKS.computeIfAbsent(key, k -> new ReentrantLock());
        lock.lock();

        boolean deferUnlock = false;
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            deferUnlock = true;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    try {
                        lock.unlock();
                    } finally {
                        SLOT_LOCKS.remove(key, lock);
                    }
                }
            });
        }

        try {
            return body.call();
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (!deferUnlock) {
                try {
                    lock.unlock();
                } finally {
                    SLOT_LOCKS.remove(key, lock);
                }
            }
        }
    }

    // 최신 BaseNode 강제 로드(미존재 시 404)
    // - from-base 동시성 구간에서 슬롯 재검사를 위해, 캐시/프록시 의존 없이 확실히 다시 읽어온다
    public BaseNode requireBaseNodeWithId(Long baseNodeId) {
        return baseNodeRepository.findById(baseNodeId)
                .orElseThrow(() -> new ApiException(ErrorCode.NODE_NOT_FOUND,
                        "BaseNode not found: " + baseNodeId));
    }


}
