package com.back.domain.user.service;

import com.back.domain.comment.repository.CommentRepository;
import com.back.domain.post.repository.PostRepository;
import com.back.domain.scenario.repository.ScenarioRepository;
import com.back.domain.user.dto.UserInfoRequest;
import com.back.domain.user.dto.UserInfoResponse;
import com.back.domain.user.dto.UserStatsResponse;
import com.back.domain.user.entity.User;
import com.back.domain.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserInfoService {

    private final UserRepository userRepository;
    private final ScenarioRepository scenarioRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    @Transactional(readOnly = true)
    public UserInfoResponse getMyInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        return UserInfoResponse.from(user);
    }

    @Transactional
    public UserInfoResponse createMyInfo(Long userId, UserInfoRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        applyPatch(user, req);

        return UserInfoResponse.from(user);
    }

    @Transactional
    public UserInfoResponse updateMyInfo(Long userId, UserInfoRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        applyPatch(user, req);

        return UserInfoResponse.from(user);
    }

    @Transactional(readOnly = true)
    public UserStatsResponse getMyStats(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        int scenarioCount = scenarioRepository.countByUserId(userId);
        int totalPoints = scenarioRepository.sumTotalByUserId(userId);
        int postCount = postRepository.countByUserId(userId);
        int commentCount = commentRepository.countByUserId(userId);

        return UserStatsResponse.of(scenarioCount, totalPoints, postCount, commentCount, user.getMbti());
    }

    private void applyPatch(User user, UserInfoRequest req) {
        if (req.username() != null)      user.setUsername(req.username());
        if (req.birthdayAt() != null)    user.setBirthdayAt(req.birthdayAt());
        if (req.gender() != null)        user.setGender(req.gender());
        if (req.mbti() != null)          user.setMbti(req.mbti());
        if (req.beliefs() != null)       user.setBeliefs(req.beliefs());
        if (req.lifeSatis() != null)     user.setLifeSatis(req.lifeSatis());
        if (req.relationship() != null)  user.setRelationship(req.relationship());
        if (req.workLifeBal() != null)   user.setWorkLifeBal(req.workLifeBal());
        if (req.riskAvoid() != null)     user.setRiskAvoid(req.riskAvoid());
    }
}
