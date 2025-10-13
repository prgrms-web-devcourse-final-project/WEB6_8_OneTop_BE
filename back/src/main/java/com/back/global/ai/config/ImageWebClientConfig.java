package com.back.global.ai.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class ImageWebClientConfig {

    @Bean("stabilityWebClient")
    @ConditionalOnProperty(prefix = "ai.image", name = "enabled", havingValue = "true")
    public WebClient stabilityWebClient(ImageAiConfig imageAiConfig) {
        return WebClient.builder()
                .baseUrl(imageAiConfig.getBaseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }
}
