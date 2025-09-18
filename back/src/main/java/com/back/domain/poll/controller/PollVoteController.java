package com.back.domain.poll.controller;

import com.back.domain.poll.service.PollVoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 투표 관련 API 요청을 처리하는 컨트롤러.
 */
@RestController
@RequestMapping("/api/v1/polls")
@RequiredArgsConstructor
public class PollVoteController {

    private final PollVoteService pollVoteService;

}