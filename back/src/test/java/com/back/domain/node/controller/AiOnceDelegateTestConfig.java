/**
 * [TEST-ONLY] AI 1회 실호출 래퍼
 * - 첫 호출만 실제 구현으로 위임하고, 이후 호출은 스텁 결과를 반환한다.
 */
package com.back.domain.node.controller;

import com.back.global.ai.client.text.TextAiClient;
import com.back.global.ai.config.SituationAiProperties;
import com.back.global.ai.vector.AIVectorService;
import com.back.global.ai.vector.AIVectorServiceImpl;
import com.back.global.ai.vector.AIVectorServiceSupportDomain;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@TestConfiguration
@Profile("test")
public class AiOnceDelegateTestConfig {

    // 한줄 요약: 테스트가 실호출 횟수를 동적으로 제어할 수 있게 예산 빈을 제공한다
    @Bean
    public AiCallBudget aiCallBudget() {
        return new AiCallBudget();
    }



    // 한줄 요약: 예산>0 이면 실제 구현, 아니면 스텁 값을 반환한다
    @Bean
    @Primary
    public AIVectorService aiOnceDelegate(
            TextAiClient textAiClient,
            AIVectorServiceSupportDomain support,
            SituationAiProperties props,
            AiCallBudget budget
    ) {
        AIVectorService real = new AIVectorServiceImpl(textAiClient, support, props);
        AIVectorService stub = (u, d, nodes) -> new AIVectorService.AiNextHint("테스트-상황(한 문장)", "테스트-추천");
        return (userId, lineId, orderedNodes) ->
                budget.consume() ? real.generateNextHint(userId, lineId, orderedNodes)
                        : stub.generateNextHint(userId, lineId, orderedNodes);
    }
}
