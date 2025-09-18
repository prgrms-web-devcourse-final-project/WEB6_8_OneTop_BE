package com.back.domain.poll.repository;

import com.back.domain.poll.entity.PollVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 투표 참여 엔티티에 대한 데이터베이스 접근을 담당하는 JpaRepository.
 */
@Repository
public interface PollVoteRepository extends JpaRepository<PollVote, Long> {
}