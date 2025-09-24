package com.back.domain.userauth.dto;

import com.back.domain.user.entity.Gender;
import com.back.domain.user.entity.Mbti;
import com.back.domain.user.entity.Role;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class UserResponse {
    private Long id;
    private String loginId;
    private String email;
    private Role role;
    private String nickname;
    private LocalDateTime birthdayAt;
    private Gender gender;
    private Mbti mbti;
    private String beliefs;
    private String authProvider;
}
