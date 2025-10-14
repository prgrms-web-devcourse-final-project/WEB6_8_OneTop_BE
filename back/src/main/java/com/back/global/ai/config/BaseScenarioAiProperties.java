/**
 * [요약] 베이스 시나리오 생성용 AI 파라미터 바인딩 전용 클래스
 * - application.yml 의 ai.baseScenario.* 값을 바인딩한다.
 */
package com.back.global.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.base-scenario")
public class BaseScenarioAiProperties {
    private int maxOutputTokens = 1000;
    private int timeoutSeconds = 60;

    // Generation Config (AI 응답 품질 제어)
    private double temperature = 0.7;
    private double topP = 0.9;
    private int topK = 40;

    // getters/setters
    public int getMaxOutputTokens() { return maxOutputTokens; }
    public void setMaxOutputTokens(int maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    public double getTopP() { return topP; }
    public void setTopP(double topP) { this.topP = topP; }
    public int getTopK() { return topK; }
    public void setTopK(int topK) { this.topK = topK; }
}
