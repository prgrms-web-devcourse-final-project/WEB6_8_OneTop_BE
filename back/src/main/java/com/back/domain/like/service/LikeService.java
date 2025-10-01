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
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final UserRepository userRepository;

    @Transactional
    public void addLike(Long userId, Long postId) {
        Post post = postRepository.findByIdWithLock(postId)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));

        if (postLikeRepository.existsByPostIdAndUserId(postId, userId)) {
            throw new ApiException(ErrorCode.POST_ALREADY_LIKED);
        }

        PostLike postLike = createPostLike(post, userId);

        postLikeRepository.save(postLike);
        post.incrementLikeCount();
    }

    @Transactional
    public void removeLike(Long postId, Long userId) {
        Post post = postRepository.findByIdWithLock(postId)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));

        boolean deleted = postLikeRepository.deleteByPostIdAndUserId(postId, userId) > 0;

        if (!deleted) {
            throw new ApiException(ErrorCode.LIKE_NOT_FOUND);
        }

        post.decrementLikeCount();
    }

    @Transactional
    public void addCommentLike(Long userId, Long postId, Long commentId) {
        Comment comment = commentRepository.findByIdWithLock(commentId)
                .orElseThrow(() -> new ApiException(ErrorCode.COMMENT_NOT_FOUND));

        if (commentLikeRepository.existsByCommentIdAndUserId(commentId, userId)) {
            throw new ApiException(ErrorCode.COMMENT_ALREADY_LIKED);
        }

        CommentLike commentLike = createCommentLike(comment, userId);

        commentLikeRepository.save(commentLike);
        comment.incrementLikeCount();
    }

    @Transactional
    public void removeCommentLike(Long userId, Long postId, Long commentId) {
        Comment comment = commentRepository.findByIdWithLock(commentId)
                .orElseThrow(() -> new ApiException(ErrorCode.COMMENT_NOT_FOUND));

        boolean deleted = commentLikeRepository.deleteByCommentIdAndUserId(commentId, userId) > 0;

        if (!deleted) {
            throw new ApiException(ErrorCode.LIKE_NOT_FOUND);
        }

        comment.decrementLikeCount();
    }

    private PostLike createPostLike(Post post, Long userId) {
        User userReference = userRepository.getReferenceById(userId);
        return PostLike.builder()
                .post(post)
                .user(userReference)
                .build();
    }

    private CommentLike createCommentLike(Comment comment, Long userId) {
        User userReference = userRepository.getReferenceById(userId);
        return CommentLike.builder()
                .comment(comment)
                .user(userReference)
                .build();
    }
}