package com.back.global.ai.service;

import com.back.domain.node.entity.BaseLine;
import com.back.domain.node.entity.DecisionLine;
import com.back.domain.node.entity.DecisionNode;
import com.back.domain.scenario.entity.Scenario;
import com.back.global.ai.dto.result.BaseScenarioResult;
import com.back.global.ai.dto.result.DecisionScenarioResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * AI 기반 시나리오 생성 서비스 인터페이스
 */
public interface AiService {
    // 베이스 시나리오 생성
    CompletableFuture<BaseScenarioResult> generateBaseScenario(BaseLine baseLine);

    // 새 시나리오 생성
    CompletableFuture<DecisionScenarioResult> generateDecisionScenario(DecisionLine decisionLine, Scenario baseScenario);

    // 상황 생성 (Trees 도메인용)
    CompletableFuture<String> generateSituation(List<DecisionNode> previousNodes);

    // 이미지 생성 TODO: 이미지 AI 결정 후 구현 예정
    default CompletableFuture<String> generateImage(String prompt) {
        return CompletableFuture.completedFuture("placeholder-image-url");
    }
}
