package com.back.domain.user.dto;

import com.back.domain.user.entity.Role;
import com.back.domain.user.entity.User;

import java.time.LocalDateTime;

/**
 * 사용자 정보를 클라이언트에 반환하기 위한 응답 DTO입니다.
 */
public record UserResponse(
        Long id,
        String email,
        String username,
        Role role,
        String nickname,
        LocalDateTime birthdayAt,
        String authProvider
) {
    public static UserResponse from(User u) {
        return new UserResponse(
                u.getId(), u.getEmail(), u.getUsername(),
                u.getRole(), u.getNickname(), u.getBirthdayAt(),
                u.getAuthProvider() == null ? "" : u.getAuthProvider().name()
        );
    }
}
