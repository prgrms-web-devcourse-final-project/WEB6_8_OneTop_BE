package com.back.domain.poll.dto;

import java.util.List;

/**
 * 투표 선택지 응답 DTO
 */
public record PollOptionResponse(
        List<Integer> selected,
        List<VoteOption> options
) {
    public record VoteOption(
            int index,
            String text
    ) {}
}
