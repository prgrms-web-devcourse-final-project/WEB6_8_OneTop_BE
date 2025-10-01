package com.back.domain.like.repository;

import com.back.domain.like.entity.CommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Set;

/**
 * 댓글 좋아요 엔티티에 대한 데이터베이스 접근을 담당하는 JpaRepository.
 */
@Repository
public interface CommentLikeRepository extends JpaRepository<CommentLike, Long> {
    @Query("SELECT cl.comment.id FROM CommentLike cl WHERE cl.user.id = :userId AND cl.comment.id IN :commentIds")
    Set<Long> findLikedCommentsIdsByUserAndCommentIds(@Param("userId") Long userId, @Param("commentIds") Set<Long> commentIds);

    boolean existsByCommentIdAndUserId(Long commentId, Long userId);

    @Modifying
    @Query("DELETE FROM CommentLike cl WHERE cl.comment.id = :commentId AND cl.user.id = :userId")
    int deleteByCommentIdAndUserId(@Param("commentId") Long commentId, @Param("userId") Long userId);
}