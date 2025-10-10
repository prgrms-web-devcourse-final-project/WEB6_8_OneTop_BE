package com.back.domain.poll.controller;

import com.back.domain.poll.dto.PollResponse;
import com.back.domain.poll.dto.VoteRequest;
import com.back.domain.poll.service.PollVoteService;
import com.back.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 투표 관련 API 요청을 처리하는 컨트롤러.
 */
@RestController
@RequestMapping("/api/v1/posts/{postId}/polls")
@RequiredArgsConstructor
public class PollVoteController {

    private final PollVoteService pollVoteService;

    @PostMapping
    public ResponseEntity<PollResponse> vote(
            @PathVariable Long postId,
            @RequestBody @Valid VoteRequest request,
            @AuthenticationPrincipal CustomUserDetails cs) {

        PollResponse response = pollVoteService.vote(cs.getUser(), postId, request);
        return ResponseEntity.ok().body(response);
    }

    @GetMapping
    public ResponseEntity<PollResponse> getVote(
            @PathVariable Long postId,
            @AuthenticationPrincipal CustomUserDetails cs) {

        PollResponse response = pollVoteService.getVote(postId);
        return ResponseEntity.ok().body(response);
    }
}