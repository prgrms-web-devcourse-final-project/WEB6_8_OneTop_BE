package com.back.domain.poll.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PollRequest(
        @NotNull
        @Size(min = 2, max = 10)
        List<PollOption> options
) {
    public record PollOption(
            @Min(value = 1, message = "투표 옵션 인덱스는 1 이상이어야 합니다.")
            int index,
            @NotNull(message = "투표 옵션 텍스트는 필수입니다.")
            String text
    ) {}
}

