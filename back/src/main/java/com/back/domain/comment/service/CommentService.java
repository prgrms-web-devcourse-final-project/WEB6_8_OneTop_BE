package com.back.domain.comment.service;

import com.back.domain.comment.dto.CommentRequest;
import com.back.domain.comment.dto.CommentResponse;
import com.back.domain.comment.entity.Comment;
import com.back.domain.comment.mapper.CommentMappers;
import com.back.domain.comment.repository.CommentRepository;
import com.back.domain.like.repository.CommentLikeRepository;
import com.back.domain.post.dto.PostSummaryResponse;
import com.back.domain.post.entity.Post;
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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 댓글 관련 비즈니스 로직을 처리하는 서비스.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository;

    @Transactional
    public CommentResponse createComment(User user, Long postId, CommentRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));

        CommentMappers.CommentCtxMapper ctxMapper = new CommentMappers.CommentCtxMapper(user, post);
        Comment savedComment = commentRepository.save(ctxMapper.toEntity(request));
        return ctxMapper.toResponse(savedComment);
    }

    public Page<CommentResponse> getComments(User user, Long postId, Pageable pageable) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));

        Page<Comment> commentsPage = commentRepository.findCommentsByPostId(postId, pageable);

        Set<Long> userLikedComments = user != null
                ? getUserLikedComments(user, commentsPage)
                : Collections.emptySet();

        return commentsPage.map(comment -> CommentMappers.toCommentResponse(
                comment,
                user,
                userLikedComments.contains(comment.getId())
        ));
    }

    @Transactional
    public Long updateComment(User user, Long commentId, CommentRequest request) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ApiException(ErrorCode.COMMENT_NOT_FOUND));

        comment.checkUser(user.getId());
        comment.updateContent(request.content());
        return comment.getId();
    }

    @Transactional
    public void deleteComment(User user, Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ApiException(ErrorCode.COMMENT_NOT_FOUND));

        comment.checkUser(user.getId());
        commentRepository.delete(comment);
    }

    private Set<Long> getUserLikedComments(User user, Page<Comment> comments) {
        Set<Long> commentIds = comments.getContent()
                .stream()
                .map(Comment::getId)
                .collect(Collectors.toSet());

        return commentLikeRepository.findLikedCommentsIdsByUserAndCommentIds(user.getId(), commentIds);
    }
}