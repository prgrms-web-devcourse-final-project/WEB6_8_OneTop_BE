package com.back.domain.post.mapper;

import com.back.domain.poll.dto.PollResponse;
import com.back.domain.post.dto.PostDetailResponse;
import com.back.domain.post.dto.PostRequest;
import com.back.domain.post.dto.PostSummaryResponse;
import com.back.domain.post.entity.Post;
import com.back.domain.post.enums.PostCategory;
import com.back.domain.user.entity.User;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PostMapper
 * 엔티티와 PostRequest, PostResponse 간의 변환을 담당하는 매퍼 클래스
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PostMappers {

    private final ObjectMapper objectMapper;

    public Post toEntity(PostRequest request, User user) {
        String voteContent = null;
        if (request.category() == PostCategory.POLL && request.poll() != null) {
            try {
                voteContent = objectMapper.writeValueAsString(request.poll());
            } catch (JsonProcessingException e) {
                throw new ApiException(ErrorCode.POLL_VOTE_INVALID_FORMAT);
            }
        }

        return Post.builder()
                .title(request.title())
                .content(request.content())
                .category(request.category())
                .user(user)
                .hide(request.hide() != null ? request.hide() : false)
                .voteContent(voteContent)
                .likeCount(0)
                .build();
    }

    public PostDetailResponse toDetailResponse(Post post, Boolean isLiked) {
        return new PostDetailResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.isHide() ? "익명" : post.getUser().getNickname(),
                post.getCategory(),
                post.getLikeCount(),
                isLiked,
                post.getCreatedDate(),
                parseVoteContent(post.getVoteContent())
        );
    }

    public PostSummaryResponse toSummaryResponse(Post post, Boolean isLiked) {
        log.info("투표 내용 {}", post.getVoteContent());
        return new PostSummaryResponse(
                post.getId(),
                post.getTitle(),
                post.getCategory().name(),
                post.isHide() ? "익명" : post.getUser().getNickname(),
                post.getCreatedDate(),
                post.getComments().size(),
                post.getLikeCount(),
                isLiked
        );
    }

    // 투표 정보 파싱 헬퍼 메서드
    private PollResponse parseVoteContent(String voteContent) {
        log.info("voteContent = {}", voteContent);
        if (voteContent == null) {
            return null;
        }
        try {
            log.info("voteContent = {}", voteContent);
            PollResponse poll = objectMapper.readValue(voteContent, PollResponse.class);
            log.info("poll = {}", poll);
            return poll;
        } catch (JsonProcessingException e) {
            log.info("e = {}", e.getMessage());
            return null;
        }
    }

}