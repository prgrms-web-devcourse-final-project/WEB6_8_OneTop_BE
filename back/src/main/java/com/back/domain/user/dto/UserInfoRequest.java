package com.back.domain.user.dto;


import com.back.domain.user.entity.Gender;
import com.back.domain.user.entity.Mbti;

import java.time.LocalDateTime;

public record UserInfoRequest(
        String username,
        LocalDateTime birthdayAt,
        Gender gender,
        Mbti mbti,
        String beliefs,
        Integer lifeSatis,
        Integer relationship,
        Integer workLifeBal,
        Integer riskAvoid
) {}
