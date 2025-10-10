package com.back.domain.like.service;

import com.back.domain.comment.entity.Comment;
import com.back.domain.comment.repository.CommentRepository;
import com.back.domain.like.entity.CommentLike;
import com.back.domain.like.entity.PostLike;
import com.back.domain.like.repository.CommentLikeRepository;
import com.back.domain.like.repository.PostLikeRepository;
import com.back.domain.post.entity.Post;
import com.back.domain.post.repository.PostRepository;
import com.back.domain.user.entity.User;
import com.back.domain.user.repository.UserRepository;
import com.back.global.common.WithLock;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.interceptor.TransactionInterceptor;

/**
 * 좋아요 관련 비즈니스 로직을 처리하는 서비스.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LikeService {

    private final PostLikeRepository postLikeRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final CommentRepository commentRepository;
    private final PostRepository postRepository;

    @Transactional
    @WithLock(key = "'post:' + #postId")
    public void addLike(User user, Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));

        if (postLikeRepository.existsByPostIdAndUserId(postId, user.getId())) {
            throw new ApiException(ErrorCode.POST_ALREADY_LIKED);
        }

        PostLike postLike = PostLike.builder()
                .post(post)
                .user(user)
                .build();

        postLikeRepository.save(postLike);
        post.incrementLikeCount();
    }

    @Transactional
    @WithLock(key = "'post:' + #postId")
    public void removeLike(User user, Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));

        boolean deleted = postLikeRepository.deleteByPostIdAndUserId(postId, user.getId()) > 0;

        if (!deleted) {
            throw new ApiException(ErrorCode.LIKE_NOT_FOUND);
        }

        post.decrementLikeCount();
    }

    @Transactional
    @WithLock(key = "'comment:' + #commentId")
    public void addCommentLike(User user, Long postId, Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ApiException(ErrorCode.COMMENT_NOT_FOUND));

        if (commentLikeRepository.existsByCommentIdAndUserId(commentId, user.getId())) {
            throw new ApiException(ErrorCode.COMMENT_ALREADY_LIKED);
        }

        CommentLike commentLike = CommentLike.builder()
                .comment(comment)
                .user(user)
                .build();

        commentLikeRepository.save(commentLike);
        comment.incrementLikeCount();
    }

    @Transactional
    @WithLock(key = "'comment:' + #commentId")
    public void removeCommentLike(User user, Long postId, Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ApiException(ErrorCode.COMMENT_NOT_FOUND));

        boolean deleted = commentLikeRepository.deleteByCommentIdAndUserId(commentId, user.getId()) > 0;

        if (!deleted) {
            throw new ApiException(ErrorCode.LIKE_NOT_FOUND);
        }

        comment.decrementLikeCount();
    }
}