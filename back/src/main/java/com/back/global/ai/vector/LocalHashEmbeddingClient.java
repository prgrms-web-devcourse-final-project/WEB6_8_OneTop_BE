/*
 * [코드 흐름 요약]
 * - 입력 텍스트를 해시 기반 고정 차원 벡터로 변환하고 L2 정규화한다.
 * - 토큰 단위 해시에 더해 바이그램/문자 셰이플릿을 선택적으로 사용해 희소성·구별력을 높인다.
 * - 배치 임베딩(embedBatch)을 제공해 대량 처리 시 호출 비용과 GC 압력을 줄인다.
 */
package com.back.global.ai.vector;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class LocalHashEmbeddingClient implements EmbeddingClient {

    private final EmbeddingProperties props;

    // 무결성 검증
    private int dim() { return Math.max(32, props.getDim()); }

    // 텍스트를 고정 차원 해시 임베딩으로 변환한다.
    @Override
    public float[] embed(String text) {
        int d = dim();
        float[] v = new float[d];
        if (text == null || text.isBlank()) return v;

        String[] toks = simpleTokenize(text);

        // next 노드 생성
        for (int i = 0; i < toks.length; i++) {
            String t = toks[i];
            if (t.isBlank()) continue;

            // 1-그램
            addHashed(v, t, 1f);

            // 바이그램(선택)
            if (props.isUseBigram() && i + 1 < toks.length) {
                addHashed(v, t + "_" + toks[i + 1], 1f);
            }

            // 문자 셰이플릿(선택)
            if (props.isUseCharShingle()) {
                for (String s : charShingles(t, 3)) addHashed(v, s, 0.3f);
            }
        }

        l2NormalizeInPlace(v);
        return v;
    }

    // 배치 임베딩(단순 루프, 구현체 일관성 보장)
    @Override
    public List<float[]> embedBatch(List<String> texts) {
        int n = texts == null ? 0 : texts.size();
        List<float[]> out = new ArrayList<>(n);
        if (n == 0) return out;
        for (String s : texts) out.add(embed(s));
        return out;
    }

    // 무결성 검증
    private String[] simpleTokenize(String text) {
        return text.toLowerCase()
                .replaceAll("[^\\p{L}\\p{Nd}\\s]", " ")
                .trim()
                .split("\\s+");
    }

    // 무결성 검증
    private List<String> charShingles(String t, int k) {
        List<String> r = new ArrayList<>();
        if (t.length() < k) return r;
        for (int i = 0; i <= t.length() - k; i++) r.add(t.substring(i, i + k));
        return r;
    }

    // 무결성 검증
    private void addHashed(float[] v, String token, float w) {
        int h = murmur32(token.getBytes(StandardCharsets.UTF_8));
        int idx = Math.floorMod(h, v.length);
        // 서명 해싱으로 편향 보정
        float sign = ((h >>> 1) & 1) == 0 ? +1f : -1f;
        v[idx] += sign * w;
    }

    // L2 정규화 수행
    private void l2NormalizeInPlace(float[] v) {
        double s = 0.0;
        for (float x : v) s += x * x;
        if (s == 0) return;
        float inv = (float) (1.0 / Math.sqrt(s));
        for (int i = 0; i < v.length; i++) v[i] *= inv;
    }

    // 간단한 MurmurHash3(32-bit) 구현
    private int murmur32(byte[] data) {
        int c1 = 0xcc9e2d51;
        int c2 = 0x1b873593;
        int r1 = 15;
        int r2 = 13;
        int m = 5;
        int n = 0xe6546b64;

        int hash = 0;
        int len4 = data.length / 4;

        for (int i = 0; i < len4; i++) {
            int i4 = i * 4;
            int k = (data[i4] & 0xff) | ((data[i4 + 1] & 0xff) << 8)
                    | ((data[i4 + 2] & 0xff) << 16) | (data[i4 + 3] << 24);
            k *= c1;
            k = Integer.rotateLeft(k, r1);
            k *= c2;

            hash ^= k;
            hash = Integer.rotateLeft(hash, r2) * m + n;
        }

        int idx = len4 * 4;
        int k1 = 0;
        switch (data.length & 3) {
            case 3 -> k1 = (data[idx + 2] & 0xff) << 16;
            case 2 -> k1 |= (data[idx + 1] & 0xff) << 8;
            case 1 -> {
                k1 |= (data[idx] & 0xff);
                k1 *= c1;
                k1 = Integer.rotateLeft(k1, r1);
                k1 *= c2;
                hash ^= k1;
            }
        }

        hash ^= data.length;
        hash ^= (hash >>> 16);
        hash *= 0x85ebca6b;
        hash ^= (hash >>> 13);
        hash *= 0xc2b2ae35;
        hash ^= (hash >>> 16);

        return hash;
    }
}
