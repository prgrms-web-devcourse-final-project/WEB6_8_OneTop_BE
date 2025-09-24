package com.back.domain.userauth.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class GuestLoginResponse {
    private String guestLoginId;
    private String nickname;
    private String role;
}
