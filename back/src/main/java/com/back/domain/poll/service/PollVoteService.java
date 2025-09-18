package com.back.domain.poll.service;

import com.back.domain.poll.repository.PollVoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 투표 관련 비즈니스 로직을 처리하는 서비스.
 */
@Service
@RequiredArgsConstructor
public class PollVoteService {

    private final PollVoteRepository pollVoteRepository;

}