package com.back.domain.like.repository;

import com.back.domain.like.entity.CommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 댓글 좋아요 엔티티에 대한 데이터베이스 접근을 담당하는 JpaRepository.
 */
@Repository
public interface CommentLikeRepository extends JpaRepository<CommentLike, Long> {
}