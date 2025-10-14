/*
 * 이 파일은 외부 API 없이 텍스트를 고정 차원(float[])으로 변환하는 경량 임베딩 클라이언트를 제공한다.
 * 토큰을 해시해서 차원에 매핑한 뒤 L2 정규화한다. 품질은 간이지만 개발/테스트/임시 운영에 충분하다.
 */
package com.back.global.ai.vector;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class LocalHashEmbeddingClient implements EmbeddingClient {

    private final EmbeddingProperties props;

    // 텍스트를 고정 차원 해시 임베딩으로 변환한다.
    @Override
    public float[] embed(String text) {
        int dim = Math.max(32, props.getDim());
        float[] v = new float[dim];
        if (text == null || text.isBlank()) return v;

        String[] toks = text.toLowerCase()
                .replaceAll("[^\\p{L}\\p{Nd}\\s]", " ")
                .trim()
                .split("\\s+");

        for (String t : toks) {
            if (t.isBlank()) continue;
            int h = murmur32(t.getBytes(StandardCharsets.UTF_8));
            int idx = Math.floorMod(h, dim);
            v[idx] += 1.0f;
        }
        l2NormalizeInPlace(v);
        return v;
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
