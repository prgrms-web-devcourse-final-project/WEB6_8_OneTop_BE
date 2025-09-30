package com.back.domain.poll.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 선택한 투표 번호
 * 다중 선택 가능 [1, 2] or [1] ...
 */
public record VoteRequest(
        @NotEmpty(message = "최소 하나의 선택지는 필수입니다")
        List<Integer> choice
) {
}
