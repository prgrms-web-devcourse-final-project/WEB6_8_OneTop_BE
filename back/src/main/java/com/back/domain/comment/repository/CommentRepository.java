package com.back.domain.comment.repository;

import com.back.domain.comment.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 댓글 엔티티에 대한 데이터베이스 접근을 담당하는 JpaRepository.
 */
@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
}