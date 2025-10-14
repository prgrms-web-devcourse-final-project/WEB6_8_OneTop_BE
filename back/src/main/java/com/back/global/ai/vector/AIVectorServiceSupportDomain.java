/*
 * 이 파일은 경로 요약 문자열 생성, pgvector 기반 관련 스니펫 검색, 스니펫 병합 유틸리티를 제공한다.
 * 항상 lineId와 현재 나이를 받아 라인 전환 시 섞임을 방지한다.
 */
package com.back.global.ai.vector;

import com.back.domain.node.entity.DecisionNode;
import com.back.domain.search.entity.NodeSnippet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AIVectorServiceSupportDomain {

    private final PgVectorSearchService vectorSearch;
    private final EmbeddingClient embeddingClient;

    // 이전 결정 경로를 간단한 요약 문자열로 만든다.
    public String buildQueryFromNodes(List<DecisionNode> nodes) {
        return nodes.stream()
                .map(n -> String.format("- (%d세) %s → %s",
                        n.getAgeYear(),
                        safe(n.getSituation()),
                        safe(n.getDecision())))
                .collect(Collectors.joining("\n"));
    }

    // 라인/나이 윈도우로 제한하여 관련 스니펫을 상위 K개 가져온다.
    public List<String> searchRelatedContexts(Long lineId, int currAge, String query, int topK, int eachSnippetLimit) {
        float[] qEmb = embeddingClient.embed(query);
        List<NodeSnippet> top = vectorSearch.topK(lineId, currAge, 2, qEmb, Math.max(topK, 1));
        List<String> out = new ArrayList<>();
        for (NodeSnippet s : top) {
            String t = s.getText();
            if (t == null || t.isBlank()) continue;
            out.add(trim(t, eachSnippetLimit));
        }
        return out;
    }

    // 여러 스니펫을 결합하되 총 길이를 제한한다.
    public String joinWithLimit(List<String> snippets, int totalCharLimit) {
        StringBuilder sb = new StringBuilder();
        for (String s : snippets) {
            if (s == null || s.isBlank()) continue;
            if (sb.length() + s.length() + 1 > totalCharLimit) break;
            if (sb.length() > 0) sb.append("\n");
            sb.append(s);
        }
        return sb.toString();
    }

    private String safe(String s) { return s == null ? "" : s.trim(); }
    private String trim(String s, int limit) {
        if (s == null) return "";
        if (s.length() <= limit) return s;
        return s.substring(0, Math.max(0, limit - 3)) + "...";
    }
}
