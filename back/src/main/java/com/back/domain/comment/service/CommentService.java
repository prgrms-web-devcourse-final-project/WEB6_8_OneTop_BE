package com.back.domain.comment.service;

import com.back.domain.comment.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 댓글 관련 비즈니스 로직을 처리하는 서비스.
 */
@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;

}