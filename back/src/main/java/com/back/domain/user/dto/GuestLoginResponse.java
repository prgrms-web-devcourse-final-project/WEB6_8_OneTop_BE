package com.back.domain.user.dto;

import com.back.domain.user.entity.User;

public record GuestLoginResponse(
        String guestEmail,
        String nickname,
        String role
) {
    public static GuestLoginResponse from(User u) {
        return new GuestLoginResponse(u.getEmail(), u.getNickname(), u.getRole().name());
    }
}
