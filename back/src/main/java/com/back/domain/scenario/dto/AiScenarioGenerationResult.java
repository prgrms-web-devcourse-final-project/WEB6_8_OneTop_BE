package com.back.domain.scenario.dto;

import com.back.global.ai.dto.result.BaseScenarioResult;
import com.back.global.ai.dto.result.DecisionScenarioResult;
import lombok.Getter;

/**
 * AI 시나리오 생성 결과를 담는 래퍼 클래스.
 * 트랜잭션 분리를 위해 베이스 시나리오와 결정 시나리오 결과를 구분하여 전달합니다.
 */
@Getter
public class AiScenarioGenerationResult {
    private final boolean isBaseScenario;
    private final BaseScenarioResult baseResult;
    private final DecisionScenarioResult decisionResult;

    // 베이스 시나리오용 생성자
    public AiScenarioGenerationResult(BaseScenarioResult baseResult) {
        this.isBaseScenario = true;
        this.baseResult = baseResult;
        this.decisionResult = null;
    }

    // 결정 시나리오용 생성자
    public AiScenarioGenerationResult(DecisionScenarioResult decisionResult) {
        this.isBaseScenario = false;
        this.baseResult = null;
        this.decisionResult = decisionResult;
    }
}
