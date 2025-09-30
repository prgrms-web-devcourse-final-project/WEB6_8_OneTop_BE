package com.back.domain.poll.service;

import com.back.domain.poll.converter.PollConverter;
import com.back.domain.poll.dto.PollResponse;
import com.back.domain.poll.dto.VoteRequest;
import com.back.domain.poll.entity.PollVote;
import com.back.domain.poll.repository.PollVoteRepository;
import com.back.domain.post.entity.Post;
import com.back.domain.post.repository.PostRepository;
import com.back.domain.user.entity.User;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 투표 관련 비즈니스 로직을 처리하는 서비스.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PollVoteService {

    private final PollVoteRepository pollVoteRepository;
    private final PostRepository postRepository;
    private final PollConverter pollConverter;

    @Transactional
    public void vote(User user, Long postId, @Valid VoteRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));

        // 요청에서 투표 항목 파싱
        PollResponse pollContent = pollConverter.fromPollJson(post.getVoteContent());

        // 유효한 옵션 검증
        validationOptions(request, pollContent);

        // 선택값 JSON 변환
        String choiceJson = pollConverter.toChoiceJson(request.choice());

        PollVote pollVote = PollVote.builder()
                .post(post)
                .pollUid(UUID.fromString(pollContent.pollUid()))
                .user(user)
                .choiceJson(choiceJson)
                .build();

        pollVoteRepository.save(pollVote);
    }

    public PollResponse getVote(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));

        // PollVote 조회 후 choiceJson 필드 파싱 [1,2], [2] ...
        // 평탄화 작업 진행 후 1, 2, 2 카운팅
        Map<Integer, Long> countMap = pollVoteRepository.findByPostId(postId).stream()
                .flatMap(pv -> pollConverter.fromChoiceJson(pv.getChoiceJson()).stream())
                .collect(Collectors.groupingBy(i -> i, Collectors.counting()));

        PollResponse pollContent = pollConverter.fromPollJson(post.getVoteContent());

        // 옵션별 voteCount 매핑
        List<PollResponse.VoteDetail> options = pollContent.options().stream()
                .map(opt -> new PollResponse.VoteDetail(
                        opt.index(),
                        opt.text(),
                        countMap.getOrDefault(opt.index(), 0L).intValue()
                ))
                .toList();

        return new PollResponse(pollContent.pollUid(), options);
    }

    private static void validationOptions(VoteRequest request, PollResponse pollContent) {
        for (Integer selectedIndex : request.choice()) {
            boolean exists = pollContent.options().stream()
                    .anyMatch(opt -> opt.index() == selectedIndex);
            if (!exists) {
                throw new ApiException(ErrorCode.POLL_VOTE_INVALID_OPTION);
            }
        }
    }
}