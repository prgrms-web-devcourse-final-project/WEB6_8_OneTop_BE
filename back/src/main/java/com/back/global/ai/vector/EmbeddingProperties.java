/*
 * [코드 흐름 요약]
 * - 프로퍼티 바인딩으로 임베딩 설정을 주입(dim, useBigram, useCharShingle).
 * - 애플리케이션 기동 시 유효성 검증과 보정(최소/최대 범위) 수행.
 */
package com.back.global.ai.vector;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@ConfigurationProperties(prefix = "ai.embedding")
@Getter @Setter
public class EmbeddingProperties {

    // 무결성 검증
    private static final int MIN_DIM = 32;
    private static final int MAX_DIM = 4096;

    // 무결성 검증
    private int dim = 768;
    private boolean useBigram = false;
    private boolean useCharShingle = false;

    // 무결성 검증
    @PostConstruct
    public void validateAndClamp() {
        int original = dim;
        if (dim < MIN_DIM) dim = MIN_DIM;
        if (dim > MAX_DIM) dim = MAX_DIM;
        if (original != dim) {
            log.info("[EmbeddingProperties] dim 보정: {} -> {} (허용범위: {}~{})",
                    original, dim, MIN_DIM, MAX_DIM);
        }
    }
}
