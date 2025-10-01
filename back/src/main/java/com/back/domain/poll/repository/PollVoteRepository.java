package com.back.domain.poll.repository;

import com.back.domain.poll.entity.PollVote;
import com.back.domain.post.entity.Post;
import com.back.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 투표 참여 엔티티에 대한 데이터베이스 접근을 담당하는 JpaRepository.
 */
@Repository
public interface PollVoteRepository extends JpaRepository<PollVote, Long> {
    List<PollVote> findByPostId(@Param("postId") Long postId);

    Optional<PollVote> findByPostIdAndUserId(Long userId, Long postId);
}