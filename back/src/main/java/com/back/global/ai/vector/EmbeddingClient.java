/*
 * [코드 흐름 요약]
 * - 단일 텍스트 임베딩(embed)과 컬렉션 배치 임베딩(embedBatch)을 정의한다.
 * - 기본 구현은 embedBatch가 embed를 루프 호출하도록 제공해 하위 호환을 보장한다.
 */
package com.back.global.ai.vector;

import java.util.ArrayList;
import java.util.List;

public interface EmbeddingClient {

    // 입력 텍스트를 임베딩 벡터로 변환한다.
    float[] embed(String text);

    // 무결성 검증
    default List<float[]> embedBatch(List<String> texts) {
        int n = texts == null ? 0 : texts.size();
        List<float[]> out = new ArrayList<>(n);
        if (n == 0) return out;
        for (String s : texts) out.add(embed(s));
        return out;
    }
}
