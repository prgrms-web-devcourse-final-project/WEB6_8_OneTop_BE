package com.back.domain.like.service;

import com.back.domain.like.repository.CommentLikeRepository;
import com.back.domain.like.repository.PostLikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 좋아요 관련 비즈니스 로직을 처리하는 서비스.
 */
@Service
@RequiredArgsConstructor
public class LikeService {

    private final PostLikeRepository postLikeRepository;
    private final CommentLikeRepository commentLikeRepository;

}