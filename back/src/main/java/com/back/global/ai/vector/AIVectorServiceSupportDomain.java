/**
 * [요약] 경로 요약/인메모리 검색 보조 유틸
 */
package com.back.global.ai.vector;

import com.back.domain.node.entity.DecisionNode;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class AIVectorServiceSupportDomain {

    // 이전 결정 경로를 요약 쿼리 문자열로 만든다
    public String buildQueryFromNodes(List<DecisionNode> nodes) {
        return nodes.stream()
                .map(n -> String.format("- (%d세) %s → %s",
                        n.getAgeYear(),
                        safe(n.getSituation()),
                        safe(n.getDecision())))
                .collect(Collectors.joining("\n"));
    }

    // 간단한 인메모리 유사 검색으로 상위 K 콘텍스트를 수집한다 (스텁)
    public List<String> searchRelatedContexts(String query, int topK, int eachSnippetLimit) {
        return Collections.emptyList(); // 추후 RAM 인덱스 교체 지점
    }

    // 여러 스니펫을 합치되 총 글자수 제한을 적용한다
    public String joinWithLimit(List<String> snippets, int totalCharLimit) {
        StringBuilder sb = new StringBuilder();
        for (String s : snippets) {
            if (s == null || s.isBlank()) continue;
            if (sb.length() + s.length() + 1 > totalCharLimit) break;
            if (!sb.isEmpty()) sb.append("\n");
            sb.append(trim(s, Math.min(s.length(), Math.max(50, totalCharLimit / 5))));
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
