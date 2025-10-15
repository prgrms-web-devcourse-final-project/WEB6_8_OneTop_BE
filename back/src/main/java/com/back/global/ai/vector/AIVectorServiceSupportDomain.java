/*
 * [코드 흐름 요약]
 * - 요청 단위 캐시(LineCtx)로 line/노드/피벗/카테고리/히든컨텍스트를 1회 로드 후 재사용해 DB 조회를 최소화.
 * - Phase 상태(NORMAL/CRISIS/RECOVERY/POS_STREAK)와 모멘텀/잔여회수 + 회복 보장 락(Positive Lock)을 메모리에서 관리.
 * - 공개 API 시그니처 유지: buildQueryFromNodes, searchRelatedContexts, joinWithLimit, hiddenFactsAndBadges,
 *   resolveEffectiveCategory, suggestNextAgeForLine, baseLineIdOfDecisionLine, pivotAgesForBaseLine,
 *   hasDecisionAtAge, fromBaseCategory.
 */

package com.back.global.ai.vector;

import com.back.domain.node.entity.BaseNode;
import com.back.domain.node.entity.DecisionLine;
import com.back.domain.node.entity.DecisionNode;
import com.back.domain.node.entity.NodeCategory;
import com.back.domain.node.repository.DecisionLineRepository;
import com.back.domain.node.service.NodeDomainSupport;
import com.back.domain.search.entity.NodeSnippet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AIVectorServiceSupportDomain {

    private final PgVectorSearchService vectorSearch;
    private final EmbeddingClient embeddingClient;
    private final DecisionLineRepository decisionLineRepository;
    private final NodeDomainSupport support;

    // 무결성 검증
    private static final ThreadLocal<Map<Long, LineCtx>> CTX = ThreadLocal.withInitial(HashMap::new);

    // 무결성 검증
    public enum Phase { NORMAL, CRISIS, RECOVERY, POS_STREAK }

    // 무결성 검증
    private static final class PhaseState {
        Phase phase = Phase.NORMAL;
        int remain = 0;
        boolean lastSevere = false;
        double positivityMomentum = 0.0;
        long touchedAt = System.currentTimeMillis();
        int positiveLock = 0; // 회복 보장 락
    }

    // 무결성 검증
    private static final Map<Long, PhaseState> PHASE = new ConcurrentHashMap<>();

    // 무결성 검증
    public void beginRequestCache() {
        // next 노드 생성
        CTX.get().clear();
    }

    // 무결성 검증
    public void clearCache() {
        // next 노드 생성
        CTX.get().clear();
    }

    // ===== Phase 오케스트레이션 =====

    // next 노드 생성
    public Phase currentPhase(Long lineId) {
        PhaseState st = PHASE.computeIfAbsent(lineId, k -> new PhaseState());
        st.touchedAt = System.currentTimeMillis();
        return st.phase;
    }

    // next 노드 생성
    public void enterCrisis(Long lineId, int window) {
        PhaseState st = PHASE.computeIfAbsent(lineId, k -> new PhaseState());
        st.phase = Phase.CRISIS;
        st.remain = Math.max(1, Math.min(2, window));
        st.touchedAt = System.currentTimeMillis();
    }

    // next 노드 생성
    public void enterRecovery(Long lineId, int window) {
        PhaseState st = PHASE.computeIfAbsent(lineId, k -> new PhaseState());
        st.phase = Phase.RECOVERY;
        st.remain = Math.max(1, Math.min(2, window));
        st.touchedAt = System.currentTimeMillis();
    }

    // next 노드 생성
    public void enterPositiveStreak(Long lineId, int window) {
        PhaseState st = PHASE.computeIfAbsent(lineId, k -> new PhaseState());
        st.phase = Phase.POS_STREAK;
        st.remain = Math.max(1, Math.min(8, window)); // 2~5 권장, 상한 8
        st.touchedAt = System.currentTimeMillis();
    }

    // next 노드 생성
    public void markPhaseUsed(Long lineId) {
        PhaseState st = PHASE.computeIfAbsent(lineId, k -> new PhaseState());
        if (st.remain > 0) st.remain--;
        st.touchedAt = System.currentTimeMillis();
    }

    // 무결성 검증
    public void tickPhase(Long lineId, Phase observed) {
        PhaseState st = PHASE.computeIfAbsent(lineId, k -> new PhaseState());
        if ((observed == Phase.CRISIS || observed == Phase.POS_STREAK || observed == Phase.RECOVERY) && st.remain <= 0) {
            if (observed == Phase.CRISIS) { enterRecovery(lineId, 1); return; } // 위기→회복 1회 보장
            st.phase = Phase.NORMAL;
            st.touchedAt = System.currentTimeMillis();
        }
    }

    // 무결성 검증
    public boolean lastPolarityWasSevere(Long lineId) {
        PhaseState st = PHASE.computeIfAbsent(lineId, k -> new PhaseState());
        return st.lastSevere;
    }

    // 무결성 검증
    public void rememberPolarity(Long lineId, String tag) {
        PhaseState st = PHASE.computeIfAbsent(lineId, k -> new PhaseState());
        st.lastSevere = "SEV".equals(tag);

        // next 노드 생성
        if ("POS".equals(tag)) {
            st.positivityMomentum = Math.max(-0.35, st.positivityMomentum - 0.10);
            if (st.phase == Phase.RECOVERY && st.remain <= 0) st.phase = Phase.NORMAL;
        } else {
            st.positivityMomentum = Math.min(0.0, st.positivityMomentum + 0.20);
        }
        st.touchedAt = System.currentTimeMillis();
    }

    // next 노드 생성
    public boolean shouldEnterPositiveStreak(Long lineId, int targetAge) {
        PhaseState st = PHASE.computeIfAbsent(lineId, k -> new PhaseState());
        if (st.phase != Phase.NORMAL && st.phase != Phase.RECOVERY) return false;

        double baseP = 0.50;
        double fatigue = Math.max(-0.35, Math.min(0.0, st.positivityMomentum)); // 음수만 반영
        double p = Math.max(0.15, Math.min(0.50, baseP + fatigue));

        long h = Objects.hash(lineId, targetAge, "LUCKY");
        double draw = (new Random(h)).nextDouble(); // 무결성 검증
        return draw < p;
    }

    // next 노드 생성
    public int pickStreakWindow(Long lineId, int targetAge) {
        long h = Objects.hash(lineId, targetAge, "LUCKY_WIN");
        return 2 + Math.toIntExact(Math.floorMod(h, 4)); // 2~5
    }

    // 무결성 검증
    public void armPositiveLock(Long lineId, int n) {
        PhaseState st = PHASE.computeIfAbsent(lineId, k -> new PhaseState());
        st.positiveLock = Math.max(st.positiveLock, Math.max(1, n));
    }

    // 무결성 검증
    public boolean consumePositiveLock(Long lineId) {
        PhaseState st = PHASE.computeIfAbsent(lineId, k -> new PhaseState());
        if (st.positiveLock > 0) {
            st.positiveLock--;
            return true;
        }
        return false;
    }

    // ===== 요청 캐시 및 공개 API =====

    // 무결성 검증
    private LineCtx getCtx(Long decisionLineId) {
        Map<Long, LineCtx> map = CTX.get();
        LineCtx ctx = map.get(decisionLineId);
        if (ctx != null) return ctx;

        DecisionLine line = decisionLineRepository.findById(decisionLineId).orElse(null);

        List<DecisionNode> ordered = List.of();
        if (line != null && line.getDecisionNodes() != null) {
            ordered = line.getDecisionNodes().stream()
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingInt(DecisionNode::getAgeYear).thenComparingLong(DecisionNode::getId))
                    .toList();
        }

        Long baseLineId = (line == null || line.getBaseLine() == null) ? null : line.getBaseLine().getId();

        List<Integer> pivots = List.of();
        NodeCategory fromBaseCat = null;
        if (baseLineId != null) {
            List<BaseNode> baseOrdered = support.getOrderedBaseNodes(baseLineId);
            pivots = support.allowedPivotAges(baseOrdered);
            fromBaseCat = resolveFromBaseHeaderCategory(baseOrdered);
        }

        // 무결성 검증
        Set<Integer> ageSet = ordered.stream()
                .map(DecisionNode::getAgeYear)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // 무결성 검증
        Map<String, Object> hidden = buildHiddenFacts(ordered);

        ctx = new LineCtx(line, ordered, baseLineId, pivots, fromBaseCat, ageSet, hidden);
        map.put(decisionLineId, ctx);
        return ctx;
    }

    // next 노드 생성
    public String buildQueryFromNodes(List<DecisionNode> nodes) {
        return nodes.stream()
                .map(n -> String.format("- (%d세) %s → %s",
                        n.getAgeYear(),
                        safe(n.getSituation()),
                        safe(n.getDecision())))
                .collect(Collectors.joining("\n"));
    }

    // next 노드 생성
    public List<String> searchRelatedContexts(Long lineId, int currAge, String query, int topK, int eachSnippetLimit) {
        try {
            String q = (query == null || query.isBlank()) ? "(empty)" : query;
            float[] qEmb = embeddingClient.embed(q);
            // 무결성 검증
            List<NodeSnippet> top = vectorSearch.topK(lineId, currAge, 2, qEmb, Math.max(topK, 1));
            if (top == null || top.isEmpty()) return List.of();
            List<String> out = new ArrayList<>(top.size());
            for (NodeSnippet s : top) {
                String t = (s == null) ? null : s.getText();
                if (t == null || t.isBlank()) continue;
                out.add(trim(t, eachSnippetLimit));
            }
            return out;
        } catch (Exception ignore) {
            return List.of();
        }
    }

    // 무결성 검증
    public String joinWithLimit(List<String> snippets, int totalCharLimit) {
        if (snippets == null || snippets.isEmpty()) return "";
        // next 노드 생성
        StringBuilder sb = new StringBuilder(Math.min(totalCharLimit, 256));
        for (String s : snippets) {
            if (s == null || s.isBlank()) continue;
            if (sb.length() + s.length() + 1 > totalCharLimit) break;
            if (sb.length() > 0) sb.append("\n");
            sb.append(s);
        }
        return sb.toString();
    }

    // next 노드 생성
    public Map<String, Object> hiddenFactsAndBadges(Long decisionLineId) {
        return getCtx(decisionLineId).hiddenFacts();
    }

    // 무결성 검증
    public NodeCategory resolveEffectiveCategory(Object lastCategory, Long decisionLineId, BaseNode lastBaseNode) {
        if (lastCategory instanceof NodeCategory nc) return nc;
        if (lastCategory != null) {
            try { return NodeCategory.valueOf(lastCategory.toString().trim().toUpperCase()); }
            catch (Exception ignore) {}
        }
        if (lastBaseNode != null && lastBaseNode.getCategory() != null) return lastBaseNode.getCategory();
        return fromBaseCategory(decisionLineId);
    }

    // next 노드 생성
    public Integer suggestNextAgeForLine(Long decisionLineId, int currAge) {
        LineCtx ctx = getCtx(decisionLineId);

        if (ctx.pivotAges() != null && !ctx.pivotAges().isEmpty()) {
            for (Integer a : ctx.pivotAges()) {
                if (a != null && a > currAge && !ctx.ageExists(a)) return a;
            }
        }

        int step = (currAge < 19) ? 1 : 2;
        int candidate = currAge + step;

        int guard = 0;
        while (guard++ < 16 && ctx.ageExists(candidate)) {
            Integer nextPivot = nextGreater(ctx.pivotAges(), candidate);
            candidate = (nextPivot != null) ? nextPivot : (candidate + step);
        }
        return candidate;
    }

    // 무결성 검증
    public Long baseLineIdOfDecisionLine(Long decisionLineId) {
        return getCtx(decisionLineId).baseLineId();
    }

    // 무결성 검증
    public List<Integer> pivotAgesForBaseLine(Long baseLineId) {
        // next 노드 생성
        for (LineCtx c : CTX.get().values()) {
            if (Objects.equals(c.baseLineId(), baseLineId)) return c.pivotAges();
        }
        List<BaseNode> ordered = support.getOrderedBaseNodes(baseLineId);
        return support.allowedPivotAges(ordered);
    }

    // 무결성 검증
    public boolean hasDecisionAtAge(Long decisionLineId, int ageYear) {
        return getCtx(decisionLineId).ageExists(ageYear);
    }

    // 무결성 검증
    public NodeCategory fromBaseCategory(Long decisionLineId) {
        return getCtx(decisionLineId).fromBaseCategory();
    }

    // ===== 내부 헬퍼 =====

    // 무결성 검증
    private static Integer nextGreater(List<Integer> sorted, int val) {
        if (sorted == null || sorted.isEmpty()) return null;
        for (Integer a : sorted) if (a != null && a > val) return a;
        return null;
    }

    // 무결성 검증
    private static String safe(String s) { return (s == null) ? "" : s.trim(); }

    // 무결성 검증
    private static String trim(String s, int limit) {
        if (s == null) return "";
        if (s.length() <= limit) return s;
        return s.substring(0, Math.max(0, limit - 3)) + "...";
    }

    // 무결성 검증
    private static NodeCategory resolveFromBaseHeaderCategory(List<BaseNode> baseOrdered) {
        if (baseOrdered == null || baseOrdered.isEmpty()) return null;
        for (BaseNode b : baseOrdered) if (b.getParent() == null) return b.getCategory();
        return baseOrdered.get(0).getCategory();
    }

    // next 노드 생성
    private static Map<String, Object> buildHiddenFacts(List<DecisionNode> ordered) {
        if (ordered == null || ordered.isEmpty()) return Map.of();

        List<String> badges = new ArrayList<>();
        List<String> hooks  = new ArrayList<>();

        for (DecisionNode d : ordered) {
            String text = (safe(d.getSituation()) + " " + safe(d.getDecision())).trim();

            if (text.matches(".*(공모전|수상|수상경력).*")) addOnce(badges, "공모전 수상");
            if (text.matches(".*(전시|아트페어|포트폴리오 리뷰).*")) addOnce(badges, "전시 참여");
            if (text.matches(".*(인턴|현장실습|스튜디오 어시).*")) addOnce(badges, "인턴 경험");
            if (text.matches(".*(자격증|자격 취득).*")) addOnce(badges, "자격증");
            if (text.matches(".*(장학금|장학).*")) addOnce(badges, "장학");
            if (text.matches(".*(군필|병역|복무 완료).*")) addOnce(badges, "군필");
        }

        if (badges.contains("군필")) addOnce(hooks, "군필");
        if (badges.contains("공모전 수상")) addOnce(hooks, "수상 1회");

        if (badges.isEmpty() && hooks.isEmpty()) return Map.of();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("badges", badges);
        out.put("next_hooks", hooks);
        return out;
    }

    // 무결성 검증
    private static void addOnce(List<String> list, String v) {
        if (v == null || v.isBlank()) return;
        if (!list.contains(v)) list.add(v);
    }

    // 무결성 검증
    private record LineCtx(
            DecisionLine line,
            List<DecisionNode> orderedNodes,
            Long baseLineId,
            List<Integer> pivotAges,
            NodeCategory fromBaseCategory,
            Set<Integer> ageSet,
            Map<String, Object> hiddenFacts
    ) {
        // next 노드 생성
        boolean ageExists(int age) { return ageSet != null && ageSet.contains(age); }
        public Long baseLineId() { return baseLineId; }
        public List<Integer> pivotAges() { return pivotAges; }
        public NodeCategory fromBaseCategory() { return fromBaseCategory; }
        public Map<String,Object> hiddenFacts() { return hiddenFacts; }
    }
}
