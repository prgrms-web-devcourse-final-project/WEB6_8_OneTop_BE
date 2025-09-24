package com.back.domain.user.dto;

public record GuestLoginResponse(
        String guestLoginId,
        String nickname,
        String role
) {}
