package com.back.domain.user.dto;

import com.back.domain.scenario.entity.Scenario;
import com.back.domain.scenario.entity.SceneType;
import com.back.domain.scenario.entity.Type;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record UserScenarioListResponse(
        Long scenarioId,
        String job,
        Map<Type, Integer> typeScores,
        int total,
        String summary
) {
    public static UserScenarioListResponse from(Scenario scenario, List<SceneType> sceneTypes) {
        Map<Type, Integer> typeScores = sceneTypes.stream()
                .collect(Collectors.toMap(
                        SceneType::getType,
                        SceneType::getPoint
                ));

        return new UserScenarioListResponse(
                scenario.getId(),
                scenario.getJob(),
                typeScores,
                scenario.getTotal(),
                scenario.getSummary()
        );
    }
}
