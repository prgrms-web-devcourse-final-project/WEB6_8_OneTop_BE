/*
 * [파일 요약]
 * - 인메모리 검색 결과(얇은 콘텍스트) + 이전 경로 요약으로 초경량 프롬프트를 만들고
 *   TextAiClient 를 동기 호출하여 JSON 2필드(situation, recommendedOption)만 추출한다.
 * - 토큰/콘텍스트 길이는 프로퍼티로 제어 가능.
 */
package com.back.global.ai.vector;

import com.back.domain.node.entity.DecisionNode;
import com.back.global.ai.client.text.TextAiClient;
import com.back.global.ai.config.SituationAiProperties;
import com.back.global.ai.dto.AiRequest;
import com.back.global.ai.prompt.SituationPrompt;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Profile("!test")
@RequiredArgsConstructor
public class AIVectorServiceImpl implements AIVectorService {

    private final TextAiClient textAiClient;
    private final AIVectorServiceSupportDomain support;
    private final SituationAiProperties props;

    // 프로퍼티 바인딩 필드
    private int topK = 5;
    private int contextCharLimit = 1000;
    private int maxOutputTokens = 384;

    public void setTopK(int topK) { this.topK = topK; }
    public void setContextCharLimit(int contextCharLimit) { this.contextCharLimit = contextCharLimit; }
    public void setMaxOutputTokens(int maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }

    /** 한 줄 요약: 경로 요약 + 얇은 콘텍스트로 프롬프트 최소화 후 AI 힌트 생성 */
    @Override
    public AiNextHint generateNextHint(Long userId, Long decisionLineId, List<DecisionNode> orderedNodes) {
        if (orderedNodes == null || orderedNodes.isEmpty()) {
            return new AiNextHint(null, null);
        }

        // 1) 질의/콘텍스트 준비
        String query = support.buildQueryFromNodes(orderedNodes);
        List<String> ctxSnippets = support.searchRelatedContexts(query, topK, Math.max(120, contextCharLimit / Math.max(1, topK)));
        String relatedContext = support.joinWithLimit(ctxSnippets, contextCharLimit);

        // 2) 초경량 RAG 프롬프트 생성
        String prompt = buildRagPrompt(query, relatedContext);

        // 3) 동기 호출 (CompletableFuture.join 사용) — 응답 즉시 필요
        AiRequest req = new AiRequest(prompt, Map.of(), Math.max(128, maxOutputTokens));
        String response = textAiClient.generateText(req).join();

        // 4) JSON 2필드만 추출
        String situation = SituationPrompt.extractSituation(response);
        String option = SituationPrompt.extractRecommendedOption(response);

        return new AiNextHint(emptyToNull(situation), emptyToNull(option));
    }

    private String buildRagPrompt(String previousSummary, String relatedContext) {
        String ctx = (relatedContext == null || relatedContext.isBlank()) ? "(관련 콘텍스트 없음)" : relatedContext;
        return """
            당신은 인생 시뮬레이션 도우미입니다.
            아래의 '이전 선택 요약'과 '관련 콘텍스트'를 참고하여,
            **동일 연도 시점**에서 자연스러운 새로운 상황을 **한 문장**으로 생성하세요.

            ## 이전 선택 요약
            %s

            ## 관련 콘텍스트(발췌)
            %s

            ### 제약
            - 반드시 현재 베이스 상황과 동일한 연/시점
            - 과장/모호 금지, 구체적이고 현실적인 한 문장
            - 선택 분기가 필요

            ### 응답(JSON)
            {
              "situation": "한 문장",
              "recommendedOption": "15자 이내 선택지"
            }
            """.formatted(previousSummary, ctx);
    }

    private String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
