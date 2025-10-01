/**
 * [요약] 상황 힌트용 AI 파라미터 바인딩 전용 클래스 (컴포넌트 금지)
 * - application.yml 의 ai.situation.* 값을 바인딩한다.
 */
package com.back.global.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.situation")
public class SituationAiProperties {
    private int topK = 5;
    private int contextCharLimit = 1000;
    private int maxOutputTokens = 384;

    // getters/setters
    public int getTopK() { return topK; }
    public void setTopK(int topK) { this.topK = topK; }
    public int getContextCharLimit() { return contextCharLimit; }
    public void setContextCharLimit(int contextCharLimit) { this.contextCharLimit = contextCharLimit; }
    public int getMaxOutputTokens() { return maxOutputTokens; }
    public void setMaxOutputTokens(int maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }
}
