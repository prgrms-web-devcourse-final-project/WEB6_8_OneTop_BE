package com.back.domain.user.dto;

import com.back.domain.user.entity.Mbti;

public record UserStatsResponse(
        int scenarioCount,
        int totalPoints,
        int postCount,
        int commentCount,
        Mbti mbti
) {
    public static UserStatsResponse of(int scenarioCount, int totalPoints, int postCount, int commentCount, Mbti mbti) {
        return new UserStatsResponse(scenarioCount, totalPoints, postCount, commentCount, mbti);
    }
}