package com.back.domain.user.dto;

import com.back.domain.user.entity.Gender;
import com.back.domain.user.entity.Mbti;
import com.back.domain.user.entity.Role;
import com.back.domain.user.entity.User;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String email,
        Role role,
        String nickname,
        LocalDateTime birthdayAt,
        Gender gender,
        Mbti mbti,
        String beliefs,
        String authProvider
) {
    public static UserResponse from(User u) {
        return new UserResponse(
                u.getId(), u.getEmail(), u.getRole(), u.getNickname(),
                u.getBirthdayAt(), u.getGender(), u.getMbti(), u.getBeliefs(),
                u.getAuthProvider() == null ? "" : u.getAuthProvider().name()
        );
    }
}
