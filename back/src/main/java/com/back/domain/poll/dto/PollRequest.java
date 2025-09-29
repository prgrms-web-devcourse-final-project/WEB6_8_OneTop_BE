package com.back.domain.poll.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public record PollRequest(
        @NotNull(message = "투표 옵션은 최소 2개 이상이어야 합니다.")
        @Size(min = 2, max = 10, message = "투표 옵션은 2개 이상 10개 이하여야 합니다.")
        List<String> options,

        @NotNull(message = "투표 종료일을 입력해주세요.")
        @Future(message = "투표 종료일은 현재 이후여야 합니다.")
        LocalDateTime endDate
) {
}
