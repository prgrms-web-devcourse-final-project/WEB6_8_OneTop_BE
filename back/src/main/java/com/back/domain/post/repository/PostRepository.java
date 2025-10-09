package com.back.domain.post.repository;

import com.back.domain.post.entity.Post;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 게시글 엔티티에 대한 데이터베이스 접근을 담당하는 JpaRepository.
 */
@Repository
public interface PostRepository extends JpaRepository<Post, Long>, PostRepositoryCustom {
//    @Lock(LockModeType.PESSIMISTIC_WRITE)
//    @Query("SELECT p FROM Post p WHERE p.id = :postId")
//    Optional<Post> findByIdWithLock(@Param("postId") Long postId);

    int countByUserId(Long userId);

    Page<Post> findByUserIdOrderByCreatedDateDesc(Long userId, Pageable pageable);
}