package com.back.domain.post.service;

import com.back.domain.post.dto.PostRequest;
import com.back.domain.post.dto.PostDetailResponse;
import com.back.domain.post.dto.PostSearchCondition;
import com.back.domain.post.dto.PostSummaryResponse;
import com.back.domain.post.entity.Post;
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

/**
 * 게시글 관련 비즈니스 로직을 처리하는 서비스.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;

    @Transactional
    public PostDetailResponse createPost(Long userId, PostRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));

        PostMappers.PostCtxMapper mapper = new PostMappers.PostCtxMapper(user);
        Post post = mapper.toEntity(request);
        Post savedPost = postRepository.save(post);

        return mapper.toResponse(savedPost);
    }

    public PostDetailResponse getPost(Long postId) {
        return postRepository.findById(postId)
                .map(PostMappers.POST_DETAIL_READ::map)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));
    }

    public Page<PostSummaryResponse> getPosts(PostSearchCondition condition, Pageable pageable) {
        return postRepository.searchPosts(condition, pageable)
                .map(PostMappers.POST_SUMMARY_READ::map);
    }

    @Transactional
    public PostDetailResponse updatePost(Long userId, Long postId, PostRequest request) {
        Post post = validatePostOwnership(userId, postId);

        post.updatePost(request.title(), request.content(), request.category());

        return PostMappers.POST_DETAIL_READ.map(post);
    }

    @Transactional
    public void deletePost(Long userId, Long postId) {
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
}