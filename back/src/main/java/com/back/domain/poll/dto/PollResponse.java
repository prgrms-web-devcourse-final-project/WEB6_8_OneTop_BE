package com.back.domain.poll.dto;

import java.util.List;

/**
 * 투표 결과를 보여주는 DTO
 */
public record PollResponse(
        String pollUid,
        List<VoteDetail> options
) {
    public record VoteDetail(
            int index,
            String text,
            Integer voteCount
    ) {}
}
