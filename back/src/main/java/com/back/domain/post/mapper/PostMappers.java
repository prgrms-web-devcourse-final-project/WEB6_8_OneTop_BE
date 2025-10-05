package com.back.domain.post.mapper;

import com.back.domain.poll.converter.PollConverter;
import com.back.domain.poll.dto.PollOptionResponse;
import com.back.domain.post.dto.PostDetailResponse;
import com.back.domain.post.dto.PostRequest;
import com.back.domain.post.dto.PostSummaryResponse;
import com.back.domain.post.entity.Post;
import com.back.domain.post.enums.PostCategory;
import com.back.domain.scenario.dto.ScenarioDetailResponse;
import com.back.domain.scenario.entity.Scenario;
import com.back.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Function;

/**
 * PostMapper
 * 엔티티와 PostRequest, PostResponse 간의 변환을 담당하는 매퍼 클래스
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PostMappers {

    private final PollConverter pollConverter;

    public Post toEntity(PostRequest request, User user, Scenario scenario) {
        String voteContent = null;
        if (request.category() == PostCategory.POLL && request.poll() != null) {
            UUID pollUid = UUID.randomUUID();
            voteContent = pollConverter.toPollContentJson(pollUid, request.poll().options());
        }

        return Post.builder()
                .title(request.title())
                .content(request.content())
                .category(request.category())
                .user(user)
                .hide(request.hide() != null ? request.hide() : false)
                .voteContent(voteContent)
                .scenario(scenario)
                .likeCount(0)
                .build();
    }

    public PostDetailResponse toDetailByCategory(
            Post post,
            boolean isLiked,
            Function<Post, PollOptionResponse> pollInfoProvider,
            Function<Post, ScenarioDetailResponse> scenarioProvider
    ) {
        return switch (post.getCategory()) {
            case CHAT -> toDetailResponse(post, isLiked);

            case POLL -> {
                PollOptionResponse pollResponse = pollInfoProvider.apply(post);
                yield toDetailWithPollsResponse(post, isLiked, pollResponse);
            }

            case SCENARIO -> {
                ScenarioDetailResponse scenarioResponse = scenarioProvider.apply(post);
                yield toDetailWithScenarioResponse(post, isLiked, scenarioResponse);
            }
        };
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
                null,
                null
        );
    }

    public PostSummaryResponse toSummaryResponse(Post post, Boolean isLiked) {
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

    public PostDetailResponse toDetailWithPollsResponse(Post post, Boolean isLiked, PollOptionResponse pollResponse) {
        return new PostDetailResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.isHide() ? "익명" : post.getUser().getNickname(),
                post.getCategory(),
                post.getLikeCount(),
                isLiked,
                post.getCreatedDate(),
                pollResponse,
                null
        );
    }

    public PostDetailResponse toDetailWithScenarioResponse(Post post, Boolean isLiked, ScenarioDetailResponse scenarioResponse) {
        return new PostDetailResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.isHide() ? "익명" : post.getUser().getNickname(),
                post.getCategory(),
                post.getLikeCount(),
                isLiked,
                post.getCreatedDate(),
                null,
                scenarioResponse
        );
    }
}