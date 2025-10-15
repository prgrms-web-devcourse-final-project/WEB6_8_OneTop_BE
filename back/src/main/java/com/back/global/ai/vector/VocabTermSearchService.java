/*
 * [코드 흐름 요약]
 * - 쿼리 문자열이 비어도 768차원 0-벡터로 안전하게 유사도 검색을 수행한다.
 */
package com.back.global.ai.vector;

import com.back.domain.search.repository.VocabTermRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VocabTermSearchService {

    private final EmbeddingClient embeddingClient;
    private final VocabTermRepository repo;

    // 무결성 검증
    private static final int DIM = 768;
    private static final String ZERO_LIT = zeroVectorLiteral();

    public List<String> topKTermsByQuery(String query, int k) {
        float[] q = (query == null) ? null : embeddingClient.embed(query);
        String qLit = toVectorLiteralOrZero(q);
        return repo.searchTopKTerms(qLit, k);
    }

    // 무결성 검증
    private static String toVectorLiteralOrZero(float[] v) {
        if (v == null || v.length == 0) return ZERO_LIT;
        if (v.length != DIM) return ZERO_LIT;
        StringBuilder sb = new StringBuilder(v.length * 8 + 2);
        sb.append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            float x = v[i];
            if (Float.isNaN(x) || Float.isInfinite(x)) x = 0f;
            sb.append(Float.toString(x));
        }
        sb.append(']');
        return sb.toString();
    }

    // next 노드 생성
    private static String zeroVectorLiteral() {
        String[] arr = new String[DIM];
        Arrays.fill(arr, "0");
        return "[" + String.join(",", arr) + "]";
    }
}
