package com.back.domain.post.service;

import com.back.domain.post.dto.PostRequest;
import com.back.domain.post.dto.PostResponse;
import com.back.domain.post.entity.Post;
import com.back.domain.post.mapper.PostMapper;
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

import java.util.List;
import java.util.Optional;

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
    public PostResponse createPost(Long userId, PostRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));

        Post post = PostMapper.toEntity(request);
        post.assignUser(user);
        Post savedPost = postRepository.save(post);

        return PostMapper.toResponse(savedPost);
    }

    public PostResponse getPost(Long postId) {
        return postRepository.findById(postId)
                .filter(post -> !post.isHide())
                .map(PostMapper::toResponse)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));
    }

    public Page<PostResponse> getPosts(Pageable pageable) {
        return postRepository.findAll(pageable)
                .map(PostMapper::toResponse);
    }

    @Transactional
    public PostResponse updatePost(Long userId, Long postId, PostRequest request) {
        Post post = validatePostOwnership(userId, postId);

        post.updatePost(request.title(), request.content(), request.category());

        return PostMapper.toResponse(post);
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