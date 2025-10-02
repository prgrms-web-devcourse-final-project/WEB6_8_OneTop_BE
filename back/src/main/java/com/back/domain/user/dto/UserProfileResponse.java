package com.back.domain.user.dto;

import com.back.domain.scenario.entity.Scenario;
import com.back.domain.scenario.entity.SceneType;
import com.back.domain.scenario.entity.Type;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record UserProfileResponse(
        String nickname,
        Long representativeScenarioId,
        String description,
        Map<Type, Integer> sceneTypePoints
) {
    public static UserProfileResponse from(String nickname, Scenario scenario, List<SceneType> sceneTypes) {
        Map<Type, Integer> pointsMap = sceneTypes.stream()
                .collect(Collectors.toMap(
                        SceneType::getType,
                        SceneType::getPoint
                ));

        return new UserProfileResponse(
                nickname,
                scenario.getId(),
                scenario.getDescription(),
                pointsMap
        );
    }
}
