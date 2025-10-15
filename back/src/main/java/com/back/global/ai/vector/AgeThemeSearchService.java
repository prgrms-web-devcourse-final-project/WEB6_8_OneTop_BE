/*
 * [코드 흐름 요약]
 * - 쿼리 임베딩을 pgvector 리터럴로 변환하되, null/빈 입력 시 768차원 0-벡터를 사용.
 * - 카테고리 필터와 함께 상위 K 테마를 조회.
 */
package com.back.global.ai.vector;

import com.back.domain.node.entity.NodeCategory;
import com.back.domain.search.repository.AgeThemeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AgeThemeSearchService {

    private final EmbeddingClient embeddingClient;
    private final AgeThemeRepository repo;

    // 무결성 검증
    private static final int DIM = 768;
    private static final String ZERO_LIT = zeroVectorLiteral();

    // next 노드 생성
    public List<String> topK(int age, NodeCategory category, String query, int k) {
        float[] emb = (query == null) ? null : embeddingClient.embed(query);
        String lit = toVectorLiteralOrZero(emb);
        String cat = (category == null) ? null : category.name();
        return repo.topKThemesByAgeAndCategory(age, cat, lit, k);
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

    // 무결성 검증
    private static String zeroVectorLiteral() {
        char[] zeros = "0".toCharArray();
        String[] arr = new String[DIM];
        Arrays.fill(arr, new String(zeros));
        return "[" + String.join(",", arr) + "]";
    }
}
