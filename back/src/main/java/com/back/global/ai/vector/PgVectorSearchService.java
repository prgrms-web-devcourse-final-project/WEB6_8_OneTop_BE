/*
 * 이 파일은 pgvector 리포지토리를 감싸 라인/나이 윈도우로 유사 스니펫을 조회하는 서비스를 제공한다.
 * 쿼리 임베딩은 float[]로 받아 SQL 캐스트 가능한 문자열 "[...]"로 변환한다.
 */
package com.back.global.ai.vector;

import com.back.domain.search.entity.NodeSnippet;
import com.back.domain.search.repository.NodeSnippetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PgVectorSearchService {

    private final NodeSnippetRepository repo;

    // 라인/나이 윈도우 필터 + pgvector 유사도 검색을 수행한다.
    public List<NodeSnippet> topK(Long lineId, int currAge, int deltaAge, float[] queryEmbedding, int k) {
        String q = toVectorLiteral(queryEmbedding);
        int minAge = currAge - deltaAge;
        int maxAge = currAge + deltaAge;
        return repo.searchTopKByLineAndAgeWindow(lineId, minAge, maxAge, q, k);
    }

    public List<String> topKText(Long lineId, int currAge, int deltaAge, float[] queryEmbedding, int k) {
        String q = toVectorLiteral(queryEmbedding);
        int minAge = currAge - deltaAge;
        int maxAge = currAge + deltaAge;
        return repo.searchTopKTextByLineAndAgeWindow(lineId, minAge, maxAge, q, k);
    }

    // float[] 임베딩을 "[a,b,c]" 형식으로 변환한다.
    private String toVectorLiteral(float[] v) {
        if (v == null || v.length == 0) return "[]";
        StringBuilder sb = new StringBuilder(v.length * 8 + 2);
        sb.append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(Float.toString(v[i]));
        }
        sb.append(']');
        return sb.toString();
    }
}
