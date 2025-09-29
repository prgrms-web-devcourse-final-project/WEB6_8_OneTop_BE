package com.back.domain.poll.dto;

import java.time.LocalDateTime;
import java.util.List;

public record PollResponse(
        List<String> options,
        LocalDateTime endDate
) {
    public record VoteOption(
            Long id,
            String text,
            Integer voteCount
    ) {}
}
