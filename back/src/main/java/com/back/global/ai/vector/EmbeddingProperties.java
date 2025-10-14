/*
 * 이 파일은 임베딩 관련 설정(dim)을 프로퍼티에서 주입받기 위한 구성 클래스를 제공한다.
 * 기본값은 768이며, ai.embedding.dim 으로 변경할 수 있다.
 */
package com.back.global.ai.vector;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "ai.embedding")
@Getter @Setter
public class EmbeddingProperties {
    private int dim = 768;
}
