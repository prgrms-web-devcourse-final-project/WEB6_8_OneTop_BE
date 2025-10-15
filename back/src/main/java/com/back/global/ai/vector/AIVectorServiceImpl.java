/*
 * [코드 흐름 요약]
 * - DB 조회 최소화(요청 단위 캐시) + 폴라리티 안정화 + 도메인 정렬:
 *   1) 트리거-카테고리 도메인 일치 시에만 강제 부정(약부정은 Phase에서 무시), 키워드 합계<2는 약부정으로 처리.
 *   2) 위기 종료 시 '회복 보장 락(positiveLock)'으로 N회 무조건 긍정 유지.
 *   3) 카테고리 정렬 few-shot(긍/부정)을 사용하여 재무 문장으로 끌리는 현상 방지.
 *   4) recent tail은 decision 중심 요약으로 부정 단어 누수 차단.
 *   5) 헤더 제외 본문만으로 콘텍스트/테마/폴라리티/검증/재시도를 1회 계산.
 */
package com.back.global.ai.vector;

import com.back.domain.node.entity.DecisionNode;
import com.back.domain.node.entity.NodeCategory;
import com.back.global.ai.bootstrap.AgeThemeSeeder;
import com.back.global.ai.bootstrap.SeedOrchestrator;
import com.back.global.ai.client.text.TextAiClient;
import com.back.global.ai.config.SituationAiProperties;
import com.back.global.ai.dto.AiRequest;
import com.back.global.ai.prompt.SituationPrompt;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Profile("!test & !test-pg")
@RequiredArgsConstructor
public class AIVectorServiceImpl implements AIVectorService {

    private final TextAiClient textAiClient;
    private final AIVectorServiceSupportDomain support;
    private final SituationAiProperties props;
    private final ObjectMapper objectMapper;
    private final VocabTermSearchService vocabSearch;
    private final AgeThemeSearchService ageThemeSearch;
    private final AgeThemeSeeder ageThemeSeeder;
    private final SeedOrchestrator seedOrchestrator;

    private int topK = 1;
    private int contextCharLimit = 200;
    private int maxOutputTokens = 64;

    public void setTopK(int topK) { this.topK = topK; }
    public void setContextCharLimit(int contextCharLimit) { this.contextCharLimit = contextCharLimit; }
    public void setMaxOutputTokens(int maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }

    // 무결성 검증
    private enum Polarity { SEVERE_NEGATIVE, POSITIVE }

    // 무결성 검증
    private enum NegTrigger {
        FINANCE_CRYPTO(new String[]{"비트코인","코인","가상화폐"}),
        FINANCE_STOCKS(new String[]{"주식","종목","etf","선물","옵션","레버리지","인버스","공매도"}),
        FINANCE_RISK(new String[]{"빚투","영끌","대출연체","신용불량","파산","압류","추심"}),
        EDU_FAIL(new String[]{"성적하락","낙제","유급","재수강","중퇴","수능폭망","입시실패","논문리젝","지도교수갈등"}),
        CAREER_LOSS(new String[]{"해고","권고사직","실직","계약해지","평가하락","프로젝트실패","성과부진"}),
        REL_BREAK(new String[]{"이별","파혼","이혼","연락두절","갈등심화","신뢰붕괴"}),
        HEALTH_STRAIN(new String[]{"과로","번아웃","수면장애","부상","질병진단","건강악화"}),
        LOC_HOUSING(new String[]{"전세사기","퇴거통보","주거불안","이사파기","침수피해","정전","단수"}),
        ETC_FAMILY(new String[]{"돌봄위기","가족간갈등","갑작스런사고","반려동물질병","일정전면취소"});

        final String[] keywords;
        NegTrigger(String[] k){ this.keywords = k; }
    }

    // 무결성 검증
    private record NegMatch(NegTrigger trigger, boolean severe, int hitCount) {}

    // next 노드 생성
    private record PhaseDecision(Polarity polarity, AIVectorServiceSupportDomain.Phase phase, NegMatch negHint) {}

    // 무결성 검증
    private enum Domain { FINANCE, EDUCATION, CAREER, RELATION, HEALTH, HOUSING, FAMILY, GENERIC }

    // 무결성 검증
    private Domain mapCategory(NodeCategory cat) {
        if (cat == null) return Domain.GENERIC;
        return switch (cat) {
            case FINANCE -> Domain.FINANCE;
            case EDUCATION -> Domain.EDUCATION;
            case CAREER -> Domain.CAREER;
            case RELATIONSHIP -> Domain.RELATION;
            case HEALTH -> Domain.HEALTH;
            case LOCATION -> Domain.HOUSING;
            default -> Domain.GENERIC;
        };
    }

    // 무결성 검증
    private Domain mapTrigger(NegTrigger t) {
        return switch (t) {
            case FINANCE_CRYPTO, FINANCE_STOCKS, FINANCE_RISK -> Domain.FINANCE;
            case EDU_FAIL -> Domain.EDUCATION;
            case CAREER_LOSS -> Domain.CAREER;
            case REL_BREAK -> Domain.RELATION;
            case HEALTH_STRAIN -> Domain.HEALTH;
            case LOC_HOUSING -> Domain.HOUSING;
            case ETC_FAMILY -> Domain.FAMILY;
        };
    }

    @Override
    public AiNextHint generateNextHint(Long userId, Long decisionLineId, List<DecisionNode> orderedNodes) {
        // next 노드 생성
        seedOrchestrator.onAiRequestEvent();
        if (orderedNodes == null || orderedNodes.isEmpty()) return new AiNextHint(null, null);

        try {
            // 무결성 검증
            support.beginRequestCache();

            List<DecisionNode> body = dropHeader(orderedNodes);
            if (body.isEmpty()) return new AiNextHint(null, null);

            DecisionNode last = body.get(body.size() - 1);
            int currAge = last.getAgeYear();

            Integer targetAge = suggestNextAgeHybrid(decisionLineId, currAge);
            NodeCategory effectiveCategory = resolveEffectiveCategory(decisionLineId, last);

            // next 노드 생성
            String recent = buildRecentTail(body, 2);
            List<String> prevOptions = collectRecentDecisions(body, 5);

            // next 노드 생성
            String queryForSearch = support.buildQueryFromNodes(body);
            List<String> ctxSnippets = support.searchRelatedContexts(
                    decisionLineId, targetAge, queryForSearch, topK, Math.max(120, contextCharLimit / Math.max(1, topK))
            );
            String relatedContext = support.joinWithLimit(ctxSnippets, contextCharLimit);

            // next 노드 생성
            List<String> ageThemes = safeAgeThemes(decisionLineId, targetAge, effectiveCategory, recent, relatedContext, 12);
            String requiredTheme = pickRequiredThemeRelaxed(ageThemes);
            Set<String> banned = new HashSet<>(labelStopWords());
            String grounding = buildGroundingForOptions(targetAge, recent, relatedContext, ageThemes, banned, prevOptions);
            String hidden = fetchHiddenContext(decisionLineId);

            String triggerSource = ((recent == null ? "" : recent) + " " + (relatedContext == null ? "" : relatedContext)).trim();

            // 무결성 검증
            AIVectorServiceSupportDomain.Phase phase = support.currentPhase(decisionLineId);
            boolean lastSev = support.lastPolarityWasSevere(decisionLineId);

            // 무결성 검증
            Polarity pol0 = decidePolaritySeeded(
                    decisionLineId, targetAge, triggerSource, phase, lastSev, effectiveCategory);
            NegMatch negMatch = detectGlobalNegative(triggerSource);

            // next 노드 생성
            PhaseDecision pdec = decidePhaseAndOverride(decisionLineId, targetAge, triggerSource, effectiveCategory, pol0, negMatch);

            String prompt1 = buildPromptFreeSituationWithPolarityAndNegHint(
                    requiredTheme, ageThemes, banned, recent, relatedContext, grounding, hidden, prevOptions,
                    pdec.polarity(), pdec.negHint(), effectiveCategory
            ) + buildRecoveryBlockIfNeeded(pdec.phase());

            AiNextHint hint = callOnce(prompt1);

            boolean needRetry =
                    !isSituationForm(hint.aiNextSituation())
                            || !isOptionForm(hint.aiNextRecommendedOption())
                            || hasDigitsOrAges(hint.aiNextSituation())
                            || violatesRequiredThemeRelaxed(hint.aiNextSituation(), requiredTheme)
                            || !matchesPolarity(hint.aiNextSituation(), hint.aiNextRecommendedOption(), pdec.polarity())
                            || violatesSafetyPolicy(hint.aiNextSituation(), hint.aiNextRecommendedOption());

            if (needRetry) {
                String prompt2 = buildPromptFreeSituationHard(
                        requiredTheme, ageThemes, banned, recent, relatedContext, grounding, hidden, prevOptions
                ) + "\n[결과 경향]\n" + buildPolarityBlock(pdec.polarity())
                        + "\n" + buildFewShotForPolarity(pdec.polarity(), effectiveCategory)
                        + "\n" + buildNegTriggerHint(pdec.negHint())
                        + buildRecoveryBlockIfNeeded(pdec.phase())
                        + "\n[안전 수칙]\n- 자해/증오/불법 조장 금지. 위반 표현 발견 시 안전한 대안으로 치환.";
                hint = callOnce(prompt2);
            }

            // 무결성 검증
            support.tickPhase(decisionLineId, pdec.phase());
            support.rememberPolarity(decisionLineId, pdec.polarity() == Polarity.POSITIVE ? "POS" : "SEV");

            return new AiNextHint(emptyToNull(hint.aiNextSituation()), emptyToNull(hint.aiNextRecommendedOption()));
        } finally {
            // 무결성 검증
            support.clearCache();
        }
    }

    // next 노드 생성
    private List<DecisionNode> dropHeader(List<DecisionNode> nodes) {
        if (nodes == null || nodes.isEmpty()) return List.of();
        if (nodes.get(0).getParent() == null) {
            if (nodes.size() == 1) return List.of();
            return new ArrayList<>(nodes.subList(1, nodes.size()));
        }
        List<DecisionNode> filtered = new ArrayList<>(nodes.size());
        for (DecisionNode n : nodes) if (n.getParent() != null) filtered.add(n);
        return filtered;
    }

    private NodeCategory resolveEffectiveCategory(Long decisionLineId, DecisionNode lastNode) {
        NodeCategory cat = toNodeCategory(lastNode != null ? lastNode.getCategory() : null);
        if (cat == null && lastNode != null && lastNode.getBaseNode() != null) {
            cat = toNodeCategory(lastNode.getBaseNode().getCategory());
        }
        if (cat == null) {
            try {
                Object fromBaseCat = support.fromBaseCategory(decisionLineId);
                cat = toNodeCategory(fromBaseCat);
            } catch (Exception ignore) {}
        }
        return cat;
    }

    private Integer suggestNextAgeHybrid(Long decisionLineId, int currAge) {
        try {
            Integer viaSupport = support.suggestNextAgeForLine(decisionLineId, currAge);
            if (viaSupport != null && viaSupport > currAge) return viaSupport;
            int step = (currAge < 19) ? 1 : 2;
            return currAge + step;
        } catch (Exception ignore) {
            int step = (currAge < 19) ? 1 : 2;
            return currAge + step;
        }
    }

    // 무결성 검증
    private String buildRecentTail(List<DecisionNode> body, int n) {
        int from = Math.max(0, body.size() - n);
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < body.size(); i++) {
            DecisionNode d = body.get(i);
            if (d.getDecision()  != null) sb.append(d.getDecision()).append(' ');
            // 상황 텍스트는 부정 트리거 노이즈가 많아 제외
        }
        return stripAges(sb.toString().trim());
    }

    private List<String> collectRecentDecisions(List<DecisionNode> body, int n) {
        int from = Math.max(0, body.size() - n);
        List<String> out = new ArrayList<>();
        for (int i = from; i < body.size(); i++) {
            DecisionNode d = body.get(i);
            if (d.getDecision() != null) out.add(d.getDecision().trim());
        }
        return out.stream().filter(s -> s != null && !s.isBlank()).distinct().toList();
    }

    private String buildPromptFreeSituation(String requiredTheme,
                                            List<String> themes,
                                            Set<String> banned,
                                            String prev,
                                            String ctx,
                                            String groundingForOptions,
                                            String hidden,
                                            List<String> prevOptions) {
        String req  = (requiredTheme == null) ? "(없음)" : requiredTheme;
        String aux  = (themes == null || themes.isEmpty()) ? "(연령대 테마 없음)"
                : String.join(", ", themes.stream().limit(6).toList());
        String bannedS = banned.isEmpty() ? "(없음)" : String.join(", ", banned);
        String ctxS = (ctx == null || ctx.isBlank()) ? "(관련 콘텍스트 없음)" : ctx;
        String prevS = (prev == null || prev.isBlank()) ? "(요약 없음)" : prev;
        String gkS = (groundingForOptions == null || groundingForOptions.isBlank()) ? "(키워드 없음)" : groundingForOptions;
        String hidS = (hidden == null || hidden.isBlank()) ? "(은닉 맥락 없음)" : hidden;
        String prevOptS = (prevOptions == null || prevOptions.isEmpty()) ? "(이전 선택 없음)" : String.join(", ", prevOptions);

        return """
        아래 지시를 철저히 따르세요.

        [출력 형식]
        - JSON 한 줄만 출력: {"situation":"문장","recommendedOption":"문장"}
        - 한국어, 개행/탭/백틱/백슬래시 금지, 숫자(연도/나이/학년/점수 등) 금지

        [상황(situation) 생성 규칙 - 자유도 최대]
        - situation은 현재 나이대·카테고리에 '자연스럽고 현실적인' 완전히 새로운 상황으로 작성한다.
        - 과거 선택/경험/결과/라인 특성은 절대 언급하지 않는다(회상/인칭/이전 노드 금지).
        - 상황 문장은 "…한 상황이다."로 끝난다.
        - 아래 [연령/카테고리 테마]를 0~1회 참고하되, 문장 자연스러움을 우선한다.

        [선택지(recommendedOption) 생성 규칙 - 이전 선택 영향]
        - recommendedOption은 상황과 '의미적으로 맞물리는' 실행 전략 1가지만 제안한다.
        - 이전에 선택했던 옵션을 암시적으로 반영해 '연속성'을 갖되, 상황과 동떨어지지 않는다.
        - 필요 시 [그라운딩 키워드], [관련 콘텍스트], [은닉 맥락]을 '직접 명시하지 말고' 암시적으로 활용한다.
        - 문장은 "…한다"로 끝나며 20자 이내로 간결하게 쓴다.

        [연령/카테고리 테마]
        %s

        [최소 금지어(메타 라벨)]
        %s

        [이전 선택 요약(상황에 사용 금지)]
        %s

        [이전 선택 목록(선택지 참고)]
        %s

        [관련 콘텍스트(발췌, 상황에 사용 금지)]
        %s

        [그라운딩 키워드(선택지 보조)]
        %s

        [은닉 맥락(선택지 보조, 직접 언급 금지)]
        %s
        """.formatted(formatThemeBlock(req, aux), bannedS, prevS, prevOptS, ctxS, gkS, hidS);
    }

    private String buildPromptFreeSituationHard(String requiredTheme,
                                                List<String> themes,
                                                Set<String> banned,
                                                String prev,
                                                String ctx,
                                                String groundingForOptions,
                                                String hidden,
                                                List<String> prevOptions) {
        String base = buildPromptFreeSituation(requiredTheme, themes, banned, prev, ctx, groundingForOptions, hidden, prevOptions);
        return base.replace("[상황(situation) 생성 규칙 - 자유도 최대]",
                "[상황(situation) 생성 규칙 - 자유도 최대]\n- 상황은 반드시 '완전히 새로운' 문장으로 작성하고 과거/이전 노드 언급 금지.\n- 문장 어미는 꼭 \"…한 상황이다.\"로 마무리.\n- 숫자 전면 금지.\n- 연령/카테고리 테마는 자연스러움 저해 시 생략 가능(0~1회).");
    }

    private String formatThemeBlock(String requiredTheme, String auxThemes) {
        if ("(없음)".equals(requiredTheme)) return auxThemes;
        return requiredTheme + " | " + auxThemes;
    }

    private String buildGroundingForOptions(int age,
                                            String recent,
                                            String relatedContext,
                                            List<String> ageThemes,
                                            Set<String> bannedAll,
                                            List<String> prevOptions) {
        String base = stripAges(((recent == null ? "" : recent) + " " + (relatedContext == null ? "" : relatedContext)).trim());

        List<String> fromVocab = List.of();
        try { fromVocab = vocabSearch.topKTermsByQuery(base, 12); } catch (Exception ignore) {}

        List<String> cand = cleanTokens(fromVocab);
        if (cand.size() < 2) cand = cleanTokens(Arrays.asList(base.replaceAll("[^가-힣\\s]", " ").split("\\s+")));

        cand = cand.stream().filter(t -> !bannedAll.contains(t)).toList();

        Set<String> prevKey = cleanTokens(prevOptions).stream().collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> themeLex = themeLexicon(ageThemes);

        List<String> merged = new ArrayList<>();
        merged.addAll(prevKey);
        for (String t : cand) if (!merged.contains(t)) merged.add(t);
        for (String t : themeLex) if (!merged.contains(t)) merged.add(t);

        if (merged.isEmpty()) merged = List.of("계획", "실천");
        if (merged.size() == 1) merged.add("실천");

        return merged.stream().distinct().limit(8).collect(Collectors.joining(", "));
    }

    private List<String> cleanTokens(Collection<String> raw) {
        if (raw == null) return List.of();
        List<String> stop = List.of("그리고","하지만","그러나","또는","또","및","대한","관련","고민","선택","준비","시간","문제","해결","필요","가능","전략","계획","방안");
        return raw.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .map(s -> s.replaceAll("\\b\\d+\\b", ""))
                .map(s -> s.replaceAll("[^가-힣]", ""))
                .filter(s -> s.length() >= 2 && s.length() <= 12)
                .filter(s -> !stop.contains(s))
                .distinct()
                .toList();
    }

    private Set<String> labelStopWords() {
        return new HashSet<>(List.of("기준","전략","계획","점검","관리","목표","원칙","규칙","방안","체계","프로세스"));
    }

    private Set<String> themeLexicon(List<String> themes) {
        if (themes == null) return Set.of();
        Set<String> out = new HashSet<>();
        for (String t : themes) {
            if (t == null) continue;
            for (String tok : t.replaceAll("[^가-힣\\s]", " ").split("\\s+")) {
                String z = tok.trim();
                if (z.length() >= 2 && z.length() <= 12) out.add(z);
            }
        }
        return out;
    }

    private List<String> safeAgeThemes(Long decisionLineId,
                                       int targetAge,
                                       NodeCategory effectiveCategory,
                                       String recent,
                                       String relatedContext,
                                       int k) {
        try {
            ageThemeSeeder.ensureSeedForAgeAsync(targetAge, 12);
            NodeCategory cat = effectiveCategory;
            String q = ((recent == null ? "" : recent) + " " + (relatedContext == null ? "" : relatedContext)).trim();
            return ageThemeSearch.topK(targetAge, cat, q, k);
        } catch (Exception ignore) {
            return List.of();
        }
    }

    private NodeCategory toNodeCategory(Object category) {
        if (category == null) return null;
        if (category instanceof NodeCategory nc) return nc;
        try { return NodeCategory.valueOf(category.toString().trim().toUpperCase()); }
        catch (Exception e) { return null; }
    }

    private String pickRequiredThemeRelaxed(List<String> ageThemes) {
        if (ageThemes == null || ageThemes.isEmpty()) return null;
        for (String t : ageThemes) {
            if (t == null) continue;
            String z = t.trim();
            if (!z.isBlank()) return z;
        }
        return null;
    }

    private boolean violatesRequiredThemeRelaxed(String s, String requiredTheme) {
        if (requiredTheme == null) return false;
        if (s == null) return true;
        return !s.contains(requiredTheme);
    }

    private AiNextHint callOnce(String prompt) {
        AiRequest req = new AiRequest(
                prompt,
                Map.of(
                        "temperature", 0.7,
                        "topP", 0.9,
                        "topK", 1,
                        "candidateCount", 1,
                        "response_mime_type", "application/json"
                ),
                maxOutputTokens
        );
        String response = textAiClient.generateText(req).join();
        String situation = SituationPrompt.extractSituation(response, objectMapper);
        String option = SituationPrompt.extractRecommendedOption(response, objectMapper);
        return new AiNextHint(emptyToNull(situation), emptyToNull(option));
    }

    private String stripAges(String text) {
        if (text == null) return "";
        String s = text;
        s = s.replaceAll("\\b\\d{1,3}\\s*세\\b", " ");
        s = s.replaceAll("\\b\\d+\\s*살\\b", " ");
        s = s.replaceAll("\\b20\\d{2}\\s*년\\b", " ");
        s = s.replaceAll("\\b\\d{1,2}\\s*학년\\b", " ");
        s = s.replaceAll("\\b\\d+\\b", " ");
        return s;
    }

    private boolean isSituationForm(String s) {
        if (s == null) return false;
        String z = s.trim();
        return z.endsWith("상황이다.");
    }

    private boolean isOptionForm(String s) {
        if (s == null) return false;
        String z = s.trim();
        return z.endsWith("한다");
    }

    private boolean hasDigitsOrAges(String s) {
        if (s == null) return false;
        return s.matches(".*\\d.*") || s.matches(".*\\b\\d{1,3}\\s*세\\b.*") || s.matches(".*\\b\\d+\\s*살\\b.*");
    }

    private String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private String fetchHiddenContext(Long decisionLineId) {
        try {
            Map<String, Object> hidden = support.hiddenFactsAndBadges(decisionLineId);
            if (hidden == null || hidden.isEmpty()) return "";
            List<String> parts = new ArrayList<>();
            Object badges = hidden.get("badges");
            Object hooks  = hidden.get("next_hooks");
            if (badges != null) parts.add("badges=" + String.valueOf(badges));
            if (hooks  != null) parts.add("hooks="  + String.valueOf(hooks));
            return String.join(" | ", parts);
        } catch (Exception ignore) {
            return "";
        }
    }

    private boolean matchesPolarity(String situation, String option, Polarity pol) {
        String z = ((situation==null?"":situation)+" "+(option==null?"":option));
        Set<String> pos = Set.of("기회","성장","호재","안정","성과","확대","강화","재투자","정비","개선","회복","안도");
        Set<String> neg = Set.of("불안","난관","상실","파산","주거","퇴거","절박","압박","길바닥","집을 잃","악화","괴로움");
        Set<String> safe = Set.of("동결한다","중단한다","축소한다","보류한다","도움을 요청한다","임시 거처를 찾는다","상담을 신청한다","지원 제도를 확인한다","채무조정을 신청한다");
        if (pol == Polarity.POSITIVE) return pos.stream().anyMatch(z::contains);
        return neg.stream().anyMatch(z::contains) && safe.stream().anyMatch(z::contains);
    }

    private String buildPolarityBlock(Polarity pol) {
        if (pol == Polarity.POSITIVE) return """
        톤: 긍정적/기회 중심
        - 상황: 성과·호재·안정 신호를 드러낸다.
        - 선택지: 확장/강화/재투자 같은 적극 전략을 선호하되 과도한 약속 금지.
        """;
        return """
        톤: 극단 부정(파국 가정, 공포·상실·불안)
        - 상황: 큰 손실과 생활 불안이 발생한 상황을 사실적으로 서술한다(자해·타해·불법·혐오 금지).
        - 선택지: 위험 통제·생계 보호·법적/공적 지원(지출 동결, 활동 중단/축소, 임시 주거, 상담, 법률·채무조정, 도움 요청)만 제안한다.
        """;
    }

    // 무결성 검증
    private String buildFewShotForPolarity(Polarity pol, NodeCategory cat) {
        String c = (cat == null) ? "GEN" : cat.name();
        if (pol == Polarity.POSITIVE) {
            return switch (c) {
                case "EDUCATION" -> """
                [예시(긍정-EDU)]
                출력→ {"situation":"학습 루틴이 안정되어 집중력이 오르는 상황이다.","recommendedOption":"핵심 과목 복습 루틴을 고정한다"}
                """;
                case "CAREER" -> """
                [예시(긍정-CAREER)]
                출력→ {"situation":"업무 협업이 매끄러워 성과가 보이는 상황이다.","recommendedOption":"역할을 명확히 나누고 진행을 점검한다"}
                """;
                default -> """
                [예시(긍정)]
                출력→ {"situation":"일정이 안정되어 계획을 재정비하는 상황이다.","recommendedOption":"우선순위를 재정렬해 실행한다"}
                """;
            };
        }
        return switch (c) {
            case "EDUCATION" -> """
            [예시(부정-EDU)]
            출력→ {"situation":"과제 부담이 늘어 체력이 떨어진 상황이다.","recommendedOption":"과목 수를 줄이고 상담을 신청한다"}
            """;
            case "CAREER" -> """
            [예시(부정-CAREER)]
            출력→ {"situation":"프로젝트 압박으로 업무 집중이 흔들리는 상황이다.","recommendedOption":"업무 범위를 조정하고 도움을 요청한다"}
            """;
            case "HOUSING" -> """
            [예시(부정-HOUSING)]
            출력→ {"situation":"거주 불안으로 생활이 흔들리는 상황이다.","recommendedOption":"임시 거처를 확보하고 지출을 동결한다"}
            """;
            default -> """
            [예시(부정)]
            출력→ {"situation":"일상 리듬이 무너져 불안이 커진 상황이다.","recommendedOption":"일정을 축소하고 상담을 신청한다"}
            """;
        };
    }

    private String buildNegTriggerHint(NegMatch match){
        if (match == null) return "";
        return switch (match.trigger()) {
            case FINANCE_CRYPTO, FINANCE_STOCKS, FINANCE_RISK -> """
            [부정 트리거 힌트]
            - 재무 손실·현금흐름 악화·채무 압박·주거 유지 곤란 같은 '결과'를 사실적으로 드러내되 과도한 자책/비하 금지.
            - 선택지는 '피해 최소화/안전/합법적 지원'(지출 동결, 투자 중단, 임시 주거, 상담, 법률·채무조정, 가족·공적 지원) 위주.
            """;
            case EDU_FAIL -> """
            [부정 트리거 힌트]
            - 학업 성과 저하/평가 실패의 압박을 기술하되 모욕적 표현 금지.
            - 선택지는 일정 조정·전략 재설계·상담/튜터링·과목 축소.
            """;
            case CAREER_LOSS -> """
            [부정 트리거 힌트]
            - 고용 불안/성과 하락/계약 해지의 여파를 기술하되 불법/보복 암시 금지.
            - 선택지는 구직 전환·지출 관리·네트워킹·재교육/자격 검토.
            """;
            case REL_BREAK -> """
            [부정 트리거 힌트]
            - 관계 단절/갈등으로 인한 생활 동요를 묘사하되 혐오/폭력 암시 금지.
            - 선택지는 거리두기·상담·일정 조정·지원망 확보.
            """;
            case HEALTH_STRAIN -> """
            [부정 트리거 힌트]
            - 과로/건강 악화로 인한 기능 저하를 묘사(자해·타해·의학 디테일 금지).
            - 선택지는 휴식·검진 예약·업무 강도 조정·수면 위생 개선.
            """;
            case LOC_HOUSING -> """
            [부정 트리거 힌트]
            - 주거 불안/퇴거 위험/재해 피해로 인한 차질을 묘사.
            - 선택지는 임시 거처·공공 지원·계약 검토·지출 동결.
            """;
            case ETC_FAMILY -> """
            [부정 트리거 힌트]
            - 돌봄/가족 이슈로 인한 일정 붕괴/감정 소모를 묘사.
            - 선택지는 도움 요청·일정 축소·임시 대안 확보.
            """;
        };
    }

    private static final Pattern BIG_MONEY = Pattern.compile(".*(\\d{8,}|억|조).*");

    private boolean isBlank(String s){ return s==null || s.isBlank(); }

    private String norm(String utter){
        return isBlank(utter) ? "" : utter.replaceAll("\\s+","").toLowerCase(Locale.ROOT);
    }

    private boolean hasHighRiskInvestTrigger(String source) {
        String u = norm(source);
        if (u.isEmpty()) return false;
        boolean hasAsset = Arrays.stream(new String[]{
                "비트코인","코인","가상화폐","주식","종목","etf","선물","옵션","레버리지","인버스","공매도"
        }).anyMatch(u::contains);
        boolean hasAction = Arrays.stream(new String[]{"투자","매수","올인","몰빵","풀매수","빚투","영끌"}).anyMatch(u::contains);
        boolean big = BIG_MONEY.matcher(u).matches();
        return hasAsset && hasAction && big;
    }

    // 무결성 검증
    private NegMatch detectGlobalNegative(String source) {
        String u = norm(source);
        if (u.isEmpty()) return null;

        int hits = 0;
        NegMatch chosen = null;

        if (Arrays.stream(NegTrigger.FINANCE_RISK.keywords).anyMatch(u::contains)) {
            hits += 2;
            chosen = new NegMatch(NegTrigger.FINANCE_RISK, true, hits);
        }

        for (NegTrigger t : NegTrigger.values()) {
            int local = 0;
            for (String k : t.keywords) if (u.contains(k)) local++;
            if (local > 0) {
                hits += local;
                boolean severe = switch (t) {
                    case FINANCE_CRYPTO, FINANCE_STOCKS, FINANCE_RISK, LOC_HOUSING -> true;
                    default -> false;
                };
                chosen = new NegMatch(t, severe, hits);
            }
        }

        if (chosen == null) return null;
        return new NegMatch(chosen.trigger(), chosen.severe(), hits);
    }

    // 무결성 검증
    private boolean shouldForceNegativeByTrigger(AIVectorServiceSupportDomain.Phase phase,
                                                 NegMatch nm,
                                                 boolean lastSevere,
                                                 Domain catDom) {
        if (nm == null) return false;
        Domain trigDom = mapTrigger(nm.trigger());
        boolean domainAligned = (trigDom == catDom) || (trigDom == Domain.FINANCE && catDom == Domain.GENERIC);

        if (!domainAligned) return false;

        boolean weak = nm.hitCount() < 2;
        if (weak) {
            if (phase == AIVectorServiceSupportDomain.Phase.RECOVERY) return false;
            if (phase == AIVectorServiceSupportDomain.Phase.POS_STREAK) return false;
            if (lastSevere) return false;
            return true;
        }
        if (phase == AIVectorServiceSupportDomain.Phase.POS_STREAK) return false;
        return true;
    }

    // 무결성 검증
    private Polarity softenByPhase(AIVectorServiceSupportDomain.Phase phase,
                                   Polarity initial,
                                   NegMatch nm) {
        if (phase == AIVectorServiceSupportDomain.Phase.RECOVERY && (nm == null || nm.hitCount() < 2))
            return Polarity.POSITIVE;
        return initial;
    }

    // 무결성 검증
    private boolean coinFlipSeeded(long seed, double positiveProb) {
        return new Random(seed).nextDouble() < positiveProb;
    }

    // 무결성 검증
    private Polarity decidePolaritySeeded(Long decisionLineId,
                                          int targetAge,
                                          String triggerSource,
                                          AIVectorServiceSupportDomain.Phase phase,
                                          boolean lastSevere,
                                          NodeCategory effectiveCategory) {
        if (support.consumePositiveLock(decisionLineId)) return Polarity.POSITIVE;

        NegMatch nm = detectGlobalNegative(triggerSource);
        Domain catDom = mapCategory(effectiveCategory);

        if (shouldForceNegativeByTrigger(phase, nm, lastSevere, catDom)) {
            return Polarity.SEVERE_NEGATIVE;
        }

        final double POSITIVE_PROB =
                (catDom == Domain.EDUCATION || catDom == Domain.CAREER) && nm == null ? 0.9 : 0.7;

        boolean hasHR = hasHighRiskInvestTrigger(triggerSource);
        long seed = Objects.hash(decisionLineId, targetAge, hasHR ? "HR" : "CF");
        boolean positive = coinFlipSeeded(seed, POSITIVE_PROB);

        Polarity base = positive ? Polarity.POSITIVE : Polarity.SEVERE_NEGATIVE;
        return softenByPhase(phase, base, nm);
    }

    // 무결성 검증
    private String buildRecoveryBlockIfNeeded(AIVectorServiceSupportDomain.Phase phase) {
        if (phase != AIVectorServiceSupportDomain.Phase.RECOVERY) return "";
        return """
        
        [회복 지시]
        - 상황: 과도한 낙담/공포 없이 '안정 회복의 단서'를 부드럽게 드러낸다(직접 과거 언급 금지).
        - 선택지: 소액부터 재정립, 지원 제도 확인, 작은 루틴 회복 등 '현실적·즉시 가능한' 행동으로 제시.
        - 표현: 과장/약속 금지, 숫자 금지, 문장 어미는 "…한다".
        """;
    }

    // 무결성 검증
    private PhaseDecision decidePhaseAndOverride(Long decisionLineId,
                                                 int targetAge,
                                                 String triggerSource,
                                                 NodeCategory effectiveCategory,
                                                 Polarity initialPolarity,
                                                 NegMatch negHint) {
        AIVectorServiceSupportDomain.Phase phase = support.currentPhase(decisionLineId);

        boolean highRisk = hasHighRiskInvestTrigger(triggerSource);
        boolean strongNeg = negHint != null && (negHint.severe() || negHint.hitCount() >= 2);

        if (phase == AIVectorServiceSupportDomain.Phase.CRISIS) {
            support.markPhaseUsed(decisionLineId);
            return new PhaseDecision(Polarity.SEVERE_NEGATIVE, phase, negHint);
        }
        if (phase == AIVectorServiceSupportDomain.Phase.RECOVERY) {
            support.markPhaseUsed(decisionLineId);
            return new PhaseDecision(Polarity.POSITIVE, phase, negHint);
        }
        if (phase == AIVectorServiceSupportDomain.Phase.POS_STREAK) {
            support.markPhaseUsed(decisionLineId);
            return new PhaseDecision(Polarity.POSITIVE, phase, negHint);
        }

        if (highRisk || strongNeg) {
            int window = 1 + (int)(Math.floorMod(Objects.hash(decisionLineId, targetAge, "CRISIS"), 2)); // 1~2회
            support.enterCrisis(decisionLineId, window);
            support.armPositiveLock(decisionLineId, 2); // 위기 직후 2회 긍정 보장
            return new PhaseDecision(Polarity.SEVERE_NEGATIVE, AIVectorServiceSupportDomain.Phase.CRISIS, negHint);
        }

        if (initialPolarity == Polarity.POSITIVE && support.shouldEnterPositiveStreak(decisionLineId, targetAge)) {
            int span = support.pickStreakWindow(decisionLineId, targetAge); // 2~5
            support.enterPositiveStreak(decisionLineId, span);
            return new PhaseDecision(Polarity.POSITIVE, AIVectorServiceSupportDomain.Phase.POS_STREAK, negHint);
        }

        return new PhaseDecision(initialPolarity, AIVectorServiceSupportDomain.Phase.NORMAL, negHint);
    }

    private String buildPromptFreeSituationWithPolarityAndNegHint(
            String requiredTheme, List<String> themes, Set<String> banned, String prev, String ctx,
            String groundingForOptions, String hidden, List<String> prevOptions, Polarity pol, NegMatch negHint,
            NodeCategory cat
    ) {
        String core    = buildPromptFreeSituation(requiredTheme, themes, banned, prev, ctx, groundingForOptions, hidden, prevOptions);
        String polBlock= buildPolarityBlock(pol);
        String fewShot = buildFewShotForPolarity(pol, cat);
        String hint    = buildNegTriggerHint(negHint);

        return """
    %s

    [결과 경향]
    %s

    %s
    %s
    """.formatted(core, polBlock, fewShot, hint);
    }
    private boolean violatesSafetyPolicy(String situation, String option) {
        String z = ((situation == null ? "" : situation) + " " + (option == null ? "" : option));

        Set<String> selfHarm = Set.of("자해", "스스로 해치", "목숨", "죽");
        Set<String> hate     = Set.of("혐오", "비하", "차별", "증오");
        Set<String> illegal  = Set.of("불법", "사기", "폭력", "협박");

        return selfHarm.stream().anyMatch(z::contains)
                || hate.stream().anyMatch(z::contains)
                || illegal.stream().anyMatch(z::contains);
    }
}
