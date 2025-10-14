package com.back.global.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import io.netty.resolver.DefaultAddressResolverGroup;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;


@Configuration
@ConfigurationProperties(prefix = "ai.text.gemini")
@EnableConfigurationProperties({
        SituationAiProperties.class,
        BaseScenarioAiProperties.class,
        DecisionScenarioAiProperties.class
})
@Data
public class TextAiConfig {
    String apiKey;
    String baseUrl = "https://generativelanguage.googleapis.com";
    String model = "gemini-2.5-flash";
    String model20 = "gemini-2.0-flash";
    int timeoutSeconds = 30;
    int maxRetries = 3;
    int retryDelaySeconds = 2;
    int maxConnections = 200;

    // ▼ 추가: 성능용 밀리초 단위 설정(필요시 yml로 노출)
    private int inferenceTimeoutMillis = 1100;      // 전체 호출 상한(p50 목표)
    private int connectTimeoutMillis   = 150;       // TCP connect
    private int readTimeoutMillis      = 900;       // 응답 수신
    private int writeTimeoutMillis     = 300;       // 요청 전송
    private int poolMaxConnections     = 200;
    private int pendingAcquireMaxCount = 1000;
    private int pendingAcquireTimeoutSeconds = 2;
    private int poolMaxIdleTimeSeconds = 300;
    private int poolMaxLifeTimeSeconds = 900;

    private Integer maxContextTokens = 8192;

    @PostConstruct
    void validateKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ai.text.gemini.api-key is missing");
        }
    }

    @Bean("geminiWebClient")
    public WebClient geminiWebClient(ObjectMapper objectMapper) {

        // 커넥션 풀: idle/lifetime/evict 설정 추가
        ConnectionProvider pool = ConnectionProvider.builder("gemini-pool")
                .maxConnections(maxConnections)                  // 200
                .pendingAcquireMaxCount(pendingAcquireMaxCount)  // 1000
                .pendingAcquireTimeout(Duration.ofSeconds(pendingAcquireTimeoutSeconds)) // 2s
                .maxIdleTime(Duration.ofMinutes(2))              // 유휴 연결 유지
                .maxLifeTime(Duration.ofMinutes(10))             // 오래된 연결 교체
                .evictInBackground(Duration.ofSeconds(30))       // 백그라운드 정리
                .lifo()                                          // 핫 커넥션 우선 재사용
                .build();

        HttpClient http = HttpClient.create(pool)
                .compress(true)
                .keepAlive(true)
                .wiretap(false)
                .secure()                                        // TLS + ALPN
                .protocol(HttpProtocol.H2)                       // ★ H2만 사용(멀티플렉싱 최대화)
                .resolver(DefaultAddressResolverGroup.INSTANCE)  // 네이티브 DNS 해소
                .responseTimeout(Duration.ofSeconds(timeoutSeconds))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(timeoutSeconds))
                        .addHandlerLast(new WriteTimeoutHandler(timeoutSeconds)))
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis);

        // ★ 쿼리스트링 key 제거(헤더만 사용) → URI 재작성/캐싱저해 방지
        // ExchangeFilterFunction apiKeyQueryFilter = ...  <<-- 삭제

        // ★ 요청/응답 타이밍 로깅(간단 버전)
        ExchangeFilterFunction timing = ExchangeFilterFunction.ofRequestProcessor(req -> {
            long start = System.nanoTime();
            return Mono.just(
                    ClientRequest.from(req)
                            .headers(h -> h.add("x-req-start-nanos", String.valueOf(start)))
                            .build()
            );
        }).andThen(ExchangeFilterFunction.ofResponseProcessor(res -> {
            return Mono.deferContextual(ctx -> {
                // 필요시 로그프레임워크로 교체
                return Mono.just(res);
            });
        }));

        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("x-goog-api-key", apiKey)                     // 헤더만 사용
                .defaultHeader(HttpHeaders.ACCEPT_ENCODING, "gzip")          // 압축 명시
                .clientConnector(new ReactorClientHttpConnector(http))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .filter(timing)
                .codecs(c -> {
                    c.defaultCodecs().maxInMemorySize(8 * 1024 * 1024);
                    c.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(objectMapper));
                    c.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(objectMapper, MediaType.APPLICATION_JSON));
                })
                .build();
    }


    public Integer getMaxContextTokens() { return maxContextTokens == null ? 8192 : maxContextTokens; }
    public void setMaxContextTokens(Integer maxContextTokens) { this.maxContextTokens = maxContextTokens; }
}
