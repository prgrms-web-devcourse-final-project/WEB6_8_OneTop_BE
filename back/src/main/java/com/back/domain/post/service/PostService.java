package com.back.domain.post.service;

import com.back.domain.post.dto.PostRequest;
import com.back.domain.post.dto.PostResponse;
import com.back.domain.post.entity.Post;
import com.back.domain.post.mapper.PostMapper;
import com.back.domain.post.repository.PostRepository;
import com.back.domain.user.entity.User;
import com.back.domain.user.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
    public PostResponse createPost(Long userId, PostRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Post post = PostMapper.toEntity(request);
        post.assignUser(user);
        Post savedPost = postRepository.save(post);

        return PostMapper.toResponse(savedPost);
    }
}