package com.back.domain.user.service;

import com.back.domain.comment.entity.Comment;
import com.back.domain.comment.repository.CommentRepository;
import com.back.domain.post.entity.Post;
import com.back.domain.post.repository.PostRepository;
import com.back.domain.scenario.entity.Scenario;
import com.back.domain.scenario.entity.ScenarioStatus;
import com.back.domain.scenario.entity.SceneType;
import com.back.domain.scenario.repository.ScenarioRepository;
import com.back.domain.scenario.repository.SceneTypeRepository;
import com.back.domain.user.dto.*;
import com.back.domain.user.entity.User;
import com.back.domain.user.repository.UserRepository;
import com.back.global.common.PageResponse;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserInfoService {

    private final UserRepository userRepository;
    private final ScenarioRepository scenarioRepository;
    private final SceneTypeRepository sceneTypeRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    public UserInfoResponse getMyInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
        return UserInfoResponse.from(user);
    }

    @Transactional
    public UserInfoResponse saveOrUpdateMyInfo(Long userId, UserInfoRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        applyPatch(user, req);

        return UserInfoResponse.from(user);
    }

    public UserStatsResponse getMyStats(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        int scenarioCount = scenarioRepository.countByUserIdAndDecisionLineIsNotNullAndStatus(
                userId, ScenarioStatus.COMPLETED);
        int totalPoints = scenarioRepository.sumTotalByUserId(userId);
        int postCount = postRepository.countByUserId(userId);
        int commentCount = commentRepository.countByUserId(userId);

        return UserStatsResponse.of(scenarioCount, totalPoints, postCount, commentCount, user.getMbti());
    }

    public PageResponse<UserScenarioListResponse> getMyScenarios(Long userId, Pageable pageable) {
        // 시나리오 조회 (베이스 시나리오 제외)
        Page<Scenario> scenarioPage = scenarioRepository
                .findByUserIdAndDecisionLineIsNotNullAndStatusOrderByCreatedDateDesc(
                        userId,
                        ScenarioStatus.COMPLETED,
                        pageable
                );

        // 시나리오 id 목록 추출
        List<Long> scenarioIds = scenarioPage.getContent().stream()
                .map(Scenario::getId)
                .toList();

        // SceneType 조회, 시나리오 id별로 그룹화
        Map<Long, List<SceneType>> sceneTypeMap = scenarioIds.isEmpty()
                ? Map.of()
                : sceneTypeRepository.findByScenarioIdIn(scenarioIds).stream()
                .collect(Collectors.groupingBy(st -> st.getScenario().getId()));

        Page<UserScenarioListResponse> responsePage = scenarioPage.map(scenario ->
                UserScenarioListResponse.from(
                        scenario,
                        sceneTypeMap.getOrDefault(scenario.getId(), List.of())
                )
        );

        return PageResponse.of(responsePage);
    }

    public PageResponse<UserPostListResponse> getMyPosts(Long userId, Pageable pageable) {
        Page<Post> postPage = postRepository.findByUserIdOrderByCreatedDateDesc(userId, pageable);

        List<Long> ids = postPage.getContent().stream().map(Post::getId).toList();

        Map<Long, Long> countMap = commentRepository.countByPostIdIn(ids).stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));

        Page<UserPostListResponse> mapped = postPage.map(p ->
                UserPostListResponse.of(p, countMap.getOrDefault(p.getId(), 0L))
        );
        return PageResponse.of(mapped);
    }

    public PageResponse<UserCommentListResponse> getMyComments(Long userId, Pageable pageable) {
        Page<Comment> commentPage = commentRepository.findByUserIdOrderByCreatedDateDesc(userId, pageable);

        Page<UserCommentListResponse> responsePage = commentPage.map(UserCommentListResponse::from);

        return PageResponse.of(responsePage);
    }

    @Transactional
    public void setProfileScenario(Long userId, Long scenarioId) {
        // 해당 시나리오가 존재하고 사용자의 것인지 확인
        Scenario scenario = scenarioRepository.findById(scenarioId)
                .orElseThrow(() -> new EntityNotFoundException("Scenario not found: " + scenarioId));

        if (!scenario.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Scenario does not belong to user");
        }

        scenarioRepository.updateRepresentativeStatus(userId, scenarioId);
    }

    public UserProfileResponse getMyProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        return scenarioRepository.findByUserIdAndRepresentativeTrue(userId)
                .map(representativeScenario -> {
                    List<SceneType> sceneTypes = sceneTypeRepository.findByScenarioIdIn(
                            List.of(representativeScenario.getId())
                    );
                    return UserProfileResponse.from(user.getUsername(), representativeScenario, sceneTypes);
                })
                .orElse(null);
    }

    private void applyPatch(User user, UserInfoRequest req) {
        if (req.username() != null)      user.setUsername(req.username().trim());
        if (req.birthdayAt() != null)    user.setBirthdayAt(req.birthdayAt());
        if (req.gender() != null)        user.setGender(req.gender());
        if (req.mbti() != null)          user.setMbti(req.mbti());
        if (req.beliefs() != null)       user.setBeliefs(req.beliefs().trim());
        if (req.lifeSatis() != null)     user.setLifeSatis(req.lifeSatis());
        if (req.relationship() != null)  user.setRelationship(req.relationship());
        if (req.workLifeBal() != null)   user.setWorkLifeBal(req.workLifeBal());
        if (req.riskAvoid() != null)     user.setRiskAvoid(req.riskAvoid());
    }
}
