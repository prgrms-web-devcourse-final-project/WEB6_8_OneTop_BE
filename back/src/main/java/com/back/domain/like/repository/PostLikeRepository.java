package com.back.domain.like.repository;

import com.back.domain.like.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 게시글 좋아요 엔티티에 대한 데이터베이스 접근을 담당하는 JpaRepository.
 */
@Repository
public interface PostLikeRepository extends JpaRepository<PostLike, Long> {
    @Modifying
    @Query("DELETE FROM PostLike pl WHERE pl.post.id = :postId AND pl.user.id = :userId")
    int deleteByPostIdAndUserId(@Param("postId") Long postId, @Param("userId") Long userId);

    boolean existsByPostIdAndUserId(Long postId, Long userId);
}