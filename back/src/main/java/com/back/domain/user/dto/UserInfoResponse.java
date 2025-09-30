package com.back.domain.user.dto;

import com.back.domain.user.entity.Gender;
import com.back.domain.user.entity.Mbti;
import com.back.domain.user.entity.User;

import java.time.LocalDateTime;

public record UserInfoResponse(
        String email,
        String username,
        String nickname,
        LocalDateTime birthdayAt,
        Gender gender,
        Mbti mbti,
        String beliefs,
        Integer lifeSatis,
        Integer relationship,
        Integer workLifeBal,
        Integer riskAvoid,
        LocalDateTime updatedAt
) {
    public static UserInfoResponse from(User user) {
        return new UserInfoResponse(
                user.getEmail(),
                user.getUsername(),
                user.getNickname(),
                user.getBirthdayAt(),
                user.getGender(),
                user.getMbti(),
                user.getBeliefs(),
                user.getLifeSatis(),
                user.getRelationship(),
                user.getWorkLifeBal(),
                user.getRiskAvoid(),
                user.getUpdatedAt()
        );
    }
}