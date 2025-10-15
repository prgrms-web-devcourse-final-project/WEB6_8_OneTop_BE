/*
 * 이 파일은 pgvector 기반 얇은 콘텍스트와 이전 경로 요약으로 초경량 프롬프트를 생성하여
 * 제미나이를 동기 호출하고 JSON 2필드(situation, recommendedOption)만 추출한다.
 * 라인 전환 섞임 방지를 위해 항상 lineId/age 윈도우 필터를 사용한다.
 */
package com.back.global.ai.vector;

import com.back.domain.node.entity.DecisionNode;
import com.back.global.ai.client.text.TextAiClient;
import com.back.global.ai.config.SituationAiProperties;
import com.back.global.ai.dto.AiRequest;
import com.back.global.ai.prompt.SituationPrompt;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Profile("!test & !test-pg")
@RequiredArgsConstructor
public class AIVectorServiceImpl implements AIVectorService {

    private final TextAiClient textAiClient;
    private final AIVectorServiceSupportDomain support;
    private final SituationAiProperties props;
    private final ObjectMapper objectMapper;

    // 프로퍼티 바인딩(기본값은 예시이며 yml로 조정)
    private int topK = 1;
    private int contextCharLimit = 200;
    private int maxOutputTokens = 48;

    public void setTopK(int topK) { this.topK = topK; }
    public void setContextCharLimit(int contextCharLimit) { this.contextCharLimit = contextCharLimit; }
    public void setMaxOutputTokens(int maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }

    // 경로 요약 + 라인/나이 윈도우 RAG로 프롬프트를 최소화한 뒤 AI 힌트를 생성한다.
    @Override
    public AiNextHint generateNextHint(Long userId, Long decisionLineId, List<DecisionNode> orderedNodes) {
        if (orderedNodes == null || orderedNodes.isEmpty()) {
            return new AiNextHint(null, null);
        }

        int currAge = orderedNodes.get(orderedNodes.size() - 1).getAgeYear();

        // 질의(경로 요약)
        String query = support.buildQueryFromNodes(orderedNodes);

        // 관련 스니펫 상위 K
        List<String> ctxSnippets = support.searchRelatedContexts(
                decisionLineId, currAge, query, topK, Math.max(120, contextCharLimit / Math.max(1, topK))
        );
        String relatedContext = support.joinWithLimit(ctxSnippets, contextCharLimit);

        // 초경량 RAG 프롬프트
        String prompt = buildRagPrompt(query, relatedContext);

        // 제미나이 동기 호출(JSON 반환 유도 옵션 포함 권장)
        AiRequest req = new AiRequest(
                prompt,
                Map.of(
                        "temperature", 0.2,
                        "topP", 0.9,
                        "topK", 1,
                        "candidateCount", 1,
                        "response_mime_type", "application/json"
                ),
                maxOutputTokens
        );
        String response = textAiClient.generateText(req).join();

        // JSON 2필드 추출
        String situation = SituationPrompt.extractSituation(response, objectMapper);
        String option = SituationPrompt.extractRecommendedOption(response, objectMapper);

        return new AiNextHint(emptyToNull(situation), emptyToNull(option));
    }

    // 프롬프트 문자열을 생성한다.
    private String buildRagPrompt(String previousSummary, String relatedContext) {
        String ctx = (relatedContext == null || relatedContext.isBlank()) ? "(관련 콘텍스트 없음)" : relatedContext;
        return """
            아래 규칙을 철저히 따르세요.

        [규칙]
        - 출력은 딱 한 줄의 JSON만(개행·주석·설명 절대 금지)
        - 모든 텍스트는 한국어만 사용(영문자 A~Z, a~z 금지)
        - 허용 문자: 한글, 숫자, 공백, 기본 문장부호(.,!?\"'()-:;·…)
        - 스키마: {"situation":"문장","recommendedOption":"15자 이내 선택지"}
        - 값에 줄바꿈/탭/백틱/백슬래시 금지

        [이전 선택 요약]
        %s

        [관련 콘텍스트(발췌)]
        %s

        [요구]
        - 동일 연/시점의 자연스러운 새로운 상황을 한국어 한 문장으로 "situation"에
        - 한국어 15자 이내의 구체 선택지를 "recommendedOption"에
        - 예: {"situation":"장학금 발표가 내일로 다가왔다.","recommendedOption":"면접 대비 정리"}
        """.formatted(previousSummary, ctx);
    }

    private String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
