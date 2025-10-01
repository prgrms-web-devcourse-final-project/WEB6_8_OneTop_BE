package com.back.domain.post.service;

import com.back.domain.like.repository.PostLikeRepository;
import com.back.domain.poll.converter.PollConverter;
import com.back.domain.poll.dto.PollOptionResponse;
import com.back.domain.poll.repository.PollVoteRepository;
import com.back.domain.post.dto.PostDetailResponse;
import com.back.domain.post.dto.PostRequest;
import com.back.domain.post.dto.PostSearchCondition;
import com.back.domain.post.dto.PostSummaryResponse;
import com.back.domain.post.entity.Post;
import com.back.domain.post.enums.PostCategory;
import com.back.domain.post.mapper.PostMappers;
import com.back.domain.post.repository.PostRepository;
import com.back.domain.user.entity.User;
import com.back.domain.user.repository.UserRepository;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 게시글 관련 비즈니스 로직을 처리하는 서비스.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final PollVoteRepository pollVoteRepository;
    private final PostMappers postMappers;
    private final PollConverter pollConverter;

    @Transactional
    public PostDetailResponse createPost(Long userId, PostRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        Post post = postMappers.toEntity(request, user);
        Post savedPost = postRepository.save(post);

        return postMappers.toDetailResponse(savedPost, false);
    }

    public PostDetailResponse getPost(Long userId, Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));

        boolean isLiked = postLikeRepository.existsByPostIdAndUserId(postId, userId);

        if (post.getCategory() == PostCategory.CHAT) {
            return postMappers.toDetailResponse(post, isLiked);
        }

        List<PollOptionResponse.VoteOption> options =
                pollConverter.fromPollOptionJson(post.getVoteContent()).options();

        List<Integer> selected = pollVoteRepository.findByPostIdAndUserId(postId, userId)
                .map(vote -> pollConverter.fromChoiceJson(vote.getChoiceJson()))
                .orElse(Collections.emptyList());

        PollOptionResponse pollResponse = new PollOptionResponse(selected, options);

        return postMappers.toDetailWithPollsResponse(post, isLiked, pollResponse);
    }

    public Page<PostSummaryResponse> getPosts(Long userId, PostSearchCondition condition, Pageable pageable) {
        Page<Post> posts = postRepository.searchPosts(condition, pageable);

        Set<Long> likedPostIds = getUserLikedPostIds(userId, posts);

        return posts.map(post -> postMappers.toSummaryResponse(
                post,
                likedPostIds.contains(post.getId())
        ));
    }

    @Transactional
    public Long updatePost(Long userId, Long postId, PostRequest request) {
        if (userId == null) throw new ApiException(ErrorCode.UNAUTHORIZED_USER);

        Post post = validatePostOwnership(userId, postId);
        post.updatePost(request.title(), request.content(), request.category());

        return postId;
    }

    @Transactional
    public void deletePost(Long userId, Long postId) {
        if (userId == null) throw new ApiException(ErrorCode.UNAUTHORIZED_USER);

        Post post = validatePostOwnership(userId, postId);
        postRepository.delete(post);
    }

    private Post validatePostOwnership(Long userId, Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));

        User requestUser = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));

        post.checkUser(requestUser);

        return post;
    }

    // 특정 사용자가 해당 페이지 내의 게시글 중에서 좋아요를 누른 게시글 ID 집합 조회
    private Set<Long> getUserLikedPostIds(Long userId, Page<Post> posts) {
        Set<Long> postIds = posts.getContent()
                .stream()
                .map(Post::getId)
                .collect(Collectors.toSet());

        return postLikeRepository.findLikedPostIdsByUserAndPostIds(userId, postIds);
    }
}
