/*
 * [코드 흐름 요약]
 * - 테스트 프로필에서 AI 호출을 1회만 실제 구현으로 위임하고, 이후에는 스텁 응답을 반환한다.
 * - 예산 빈(AiCallBudget)의 consume()이 true면 실제 구현(AIVectorServiceImpl) 실행, 아니면 스텁 반환.
 * - 실제 구현은 VocabTermSearchService, AgeThemeSearchService, AgeThemeSeeder, SeedOrchestrator를 주입해 동작한다.
 * - 실제 구현 경로로 들어가면 AIVectorServiceImpl이 SeedOrchestrator.onAiRequestEvent()를 호출하여
 *   30분 Quiet Wait 로직이 작동한다.
 */
package com.back.domain.node.controller;

import com.back.global.ai.bootstrap.AgeThemeSeeder;
import com.back.global.ai.bootstrap.SeedOrchestrator; // ★ 추가
import com.back.global.ai.client.text.TextAiClient;
import com.back.global.ai.config.SituationAiProperties;
import com.back.global.ai.vector.AIVectorService;
import com.back.global.ai.vector.AIVectorServiceImpl;
import com.back.global.ai.vector.AIVectorServiceSupportDomain;
import com.back.global.ai.vector.AgeThemeSearchService;
import com.back.global.ai.vector.VocabTermSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@TestConfiguration
@Profile("test")
public class AiOnceDelegateTestConfig {

    @Bean
    public AiCallBudget aiCallBudget() {
        return new AiCallBudget();
    }

    @Bean
    @Primary
    public AIVectorService aiOnceDelegate(
            TextAiClient textAiClient,
            AIVectorServiceSupportDomain support,
            SituationAiProperties props,
            ObjectMapper objectMapper,
            VocabTermSearchService vocabSearch,
            AgeThemeSearchService ageThemeSearch,
            AgeThemeSeeder ageThemeSeeder,
            SeedOrchestrator seedOrchestrator,
            AiCallBudget budget
    ) {
        // 실제 구현 인스턴스 (SeedOrchestrator 포함)
        AIVectorService real = new AIVectorServiceImpl(
                textAiClient,
                support,
                props,
                objectMapper,
                vocabSearch,
                ageThemeSearch,
                ageThemeSeeder,
                seedOrchestrator
        );

        // 스텁 구현
        AIVectorService stub = (u, d, nodes) ->
                new AIVectorService.AiNextHint("테스트-상황이다.", "테스트-추천한다");

        // 1회만 실제 호출, 이후 스텁
        return (userId, lineId, orderedNodes) ->
                budget.consume()
                        ? real.generateNextHint(userId, lineId, orderedNodes)
                        : stub.generateNextHint(userId, lineId, orderedNodes);
    }
}
