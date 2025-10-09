/**
 * [요약] 결정 시나리오 생성용 AI 파라미터 바인딩 전용 클래스
 * - application.yml 의 ai.decisionScenario.* 값을 바인딩한다.
 */
package com.back.global.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.decision-scenario")
public class DecisionScenarioAiProperties {
    private int maxOutputTokens = 1200;

    // getters/setters
    public int getMaxOutputTokens() { return maxOutputTokens; }
    public void setMaxOutputTokens(int maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }
}
