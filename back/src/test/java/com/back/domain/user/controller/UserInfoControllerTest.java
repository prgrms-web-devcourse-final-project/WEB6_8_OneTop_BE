package com.back.domain.user.controller;

import com.back.domain.comment.entity.Comment;
import com.back.domain.comment.repository.CommentRepository;
import com.back.domain.node.entity.BaseLine;
import com.back.domain.node.entity.DecisionLine;
import com.back.domain.node.entity.DecisionLineStatus;
import com.back.domain.node.repository.BaseLineRepository;
import com.back.domain.node.repository.DecisionLineRepository;
import com.back.domain.post.entity.Post;
import com.back.domain.post.enums.PostCategory;
import com.back.domain.post.repository.PostRepository;
import com.back.domain.scenario.entity.Scenario;
import com.back.domain.scenario.entity.ScenarioStatus;
import com.back.domain.scenario.repository.ScenarioRepository;
import com.back.domain.user.dto.UserInfoRequest;
import com.back.domain.user.entity.*;
import com.back.domain.user.repository.UserRepository;
import com.back.global.security.CustomUserDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class UserInfoControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ScenarioRepository scenarioRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private BaseLineRepository baseLineRepository;

    @Autowired
    private DecisionLineRepository decisionLineRepository;

    private User testUser;
    private UsernamePasswordAuthenticationToken authentication;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("test@example.com")
                .password("password")
                .username("TestUser")
                .nickname("testnick")
                .birthdayAt(LocalDateTime.of(1990, 1, 1, 0, 0))
                .gender(Gender.M)
                .mbti(Mbti.INFP)
                .beliefs("Test beliefs")
                .lifeSatis(7)
                .relationship(8)
                .workLifeBal(6)
                .riskAvoid(5)
                .role(Role.USER)
                .authProvider(AuthProvider.LOCAL)
                .build();
        testUser = userRepository.save(testUser);

        CustomUserDetails userDetails = new CustomUserDetails(testUser);
        authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
    }

    // 헬퍼 메서드

    private Post createPost(User user, String title, PostCategory category) {
        Post post = Post.builder()
                .user(user)
                .title(title)
                .content("Content for " + title)
                .category(category)
                .hide(false)
                .build();
        return postRepository.save(post);
    }

    private Comment createComment(User user, Post post, String content) {
        Comment comment = Comment.builder()
                .user(user)
                .post(post)
                .content(content)
                .hide(false)
                .build();
        return commentRepository.save(comment);
    }

    private User createOtherUser() {
        return userRepository.save(User.builder()
                .email("other@example.com")
                .password("password")
                .username("OtherUser")
                .nickname("othernick")
                .birthdayAt(LocalDateTime.of(1995, 5, 15, 0, 0))
                .role(Role.USER)
                .authProvider(AuthProvider.LOCAL)
                .build());
    }

    private BaseLine createBaseLine(User user, String title) {
        BaseLine baseLine = BaseLine.builder()
                .user(user)
                .title(title)
                .build();
        return baseLineRepository.save(baseLine);
    }

    private DecisionLine createDecisionLine(User user, BaseLine baseLine) {
        DecisionLine decisionLine = DecisionLine.builder()
                .user(user)
                .baseLine(baseLine)
                .status(DecisionLineStatus.COMPLETED)
                .build();
        return decisionLineRepository.save(decisionLine);
    }

    private Scenario createScenario(User user, BaseLine baseLine, DecisionLine decisionLine,
                                    String job, int total, String summary,
                                    ScenarioStatus status, boolean representative) {
        Scenario scenario = Scenario.builder()
                .user(user)
                .baseLine(baseLine)
                .decisionLine(decisionLine)
                .status(status)
                .job(job)
                .total(total)
                .summary(summary)
                .description("Test description for " + job)
                .representative(representative)
                .build();
        return scenarioRepository.save(scenario);
    }

    private Scenario createScenario(User user, BaseLine baseLine, DecisionLine decisionLine,
                                    String job, int total, String summary) {
        return createScenario(user, baseLine, decisionLine, job, total, summary,
                ScenarioStatus.COMPLETED, false);
    }

    @Test
    @DisplayName("성공 - 사용자 통계 정보 조회 성공")
    void t1() throws Exception {
        BaseLine baseLine = createBaseLine(testUser, "Test BaseLine");
        DecisionLine decisionLine = createDecisionLine(testUser, baseLine);
        createScenario(testUser, baseLine, decisionLine, "Software Engineer", 100, "Test summary 1");

        Post post = createPost(testUser, "Test Post 1", PostCategory.CHAT);
        createComment(testUser, post, "Comment 1");

        mockMvc.perform(get("/api/v1/users/use-log")
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenarioCount").value(1))
                .andExpect(jsonPath("$.totalPoints").value(100))
                .andExpect(jsonPath("$.postCount").value(1))
                .andExpect(jsonPath("$.commentCount").value(1))
                .andExpect(jsonPath("$.mbti").value("INFP"));
    }

    @Test
    @DisplayName("성공 - 사용자 정보 조회 성공")
    void t2() throws Exception {
        mockMvc.perform(get("/api/v1/users-info")
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.username").value("TestUser"))
                .andExpect(jsonPath("$.nickname").value("testnick"))
                .andExpect(jsonPath("$.gender").value("M"))
                .andExpect(jsonPath("$.mbti").value("INFP"))
                .andExpect(jsonPath("$.beliefs").value("Test beliefs"))
                .andExpect(jsonPath("$.lifeSatis").value(7))
                .andExpect(jsonPath("$.relationship").value(8))
                .andExpect(jsonPath("$.workLifeBal").value(6))
                .andExpect(jsonPath("$.riskAvoid").value(5));
    }

    @Test
    @DisplayName("성공 - 사용자 정보 생성 성공")
    void t3() throws Exception {
        UserInfoRequest request = new UserInfoRequest(
                "UpdatedUser",
                LocalDateTime.of(1995, 5, 15, 0, 0),
                Gender.F,
                Mbti.ENFJ,
                "Updated beliefs",
                9,
                7,
                8,
                6
        );

        mockMvc.perform(post("/api/v1/users-info")
                        .with(authentication(authentication))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("UpdatedUser"))
                .andExpect(jsonPath("$.gender").value("F"))
                .andExpect(jsonPath("$.mbti").value("ENFJ"))
                .andExpect(jsonPath("$.beliefs").value("Updated beliefs"))
                .andExpect(jsonPath("$.lifeSatis").value(9))
                .andExpect(jsonPath("$.relationship").value(7))
                .andExpect(jsonPath("$.workLifeBal").value(8))
                .andExpect(jsonPath("$.riskAvoid").value(6));
    }

    @Test
    @DisplayName("성공 - 사용자 정보 수정 성공")
    void t4() throws Exception {
        UserInfoRequest request = new UserInfoRequest(
                "ModifiedUser",
                null,
                null,
                Mbti.ISTJ,
                null,
                10,
                null,
                null,
                null
        );

        mockMvc.perform(put("/api/v1/users-info")
                        .with(authentication(authentication))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("ModifiedUser"))
                .andExpect(jsonPath("$.mbti").value("ISTJ"))
                .andExpect(jsonPath("$.lifeSatis").value(10))
                .andExpect(jsonPath("$.gender").value("M"))
                .andExpect(jsonPath("$.relationship").value(8));
    }

    @Test
    @DisplayName("실패 - 인증되지 않은 사용자 정보 조회 실패")
    void t5() throws Exception {
        mockMvc.perform(get("/api/v1/users-info"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("성공 - null 필드는 업데이트하지 않는 부분 검증")
    void t6() throws Exception {
        UserInfoRequest request = new UserInfoRequest(
                null,
                null,
                null,
                null,
                "Only beliefs updated",
                null,
                null,
                null,
                null
        );

        mockMvc.perform(put("/api/v1/users-info")
                        .with(authentication(authentication))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.beliefs").value("Only beliefs updated"))
                .andExpect(jsonPath("$.username").value("TestUser"))
                .andExpect(jsonPath("$.mbti").value("INFP"));
    }

    @Test
    @DisplayName("성공 - 내 시나리오 목록 조회 성공")
    void t7() throws Exception {
        BaseLine baseLine = createBaseLine(testUser, "Test BaseLine");

        // 베이스 시나리오
        createScenario(testUser, baseLine, null, "Base Job", 100, "Base scenario summary");

        // 완료된 시나리오 2개
        DecisionLine decisionLine1 = createDecisionLine(testUser, baseLine);
        createScenario(testUser, baseLine, decisionLine1, "Software Engineer", 425,
                "대학원 진학 후 AI 연구원으로 성장");

        DecisionLine decisionLine2 = createDecisionLine(testUser, baseLine);
        createScenario(testUser, baseLine, decisionLine2, "Freelancer Developer", 375,
                "자유로운 근무 환경에서 다양한 프로젝트 수행");

        // 진행중인 시나리오 (제외되어야 함)
        DecisionLine decisionLine3 = createDecisionLine(testUser, baseLine);
        createScenario(testUser, baseLine, decisionLine3, "Designer", 350,
                "진행중인 시나리오", ScenarioStatus.PROCESSING, false);

        mockMvc.perform(get("/api/v1/users/list")
                        .param("page", "0")
                        .param("size", "10")
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].job").value("Freelancer Developer"))
                .andExpect(jsonPath("$.items[0].total").value(375))
                .andExpect(jsonPath("$.items[1].job").value("Software Engineer"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    @DisplayName("성공 - 시나리오가 없을 때 빈 목록 반환")
    void t8() throws Exception {
        mockMvc.perform(get("/api/v1/users/list")
                        .param("page", "0")
                        .param("size", "10")
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    @DisplayName("성공 - 페이지네이션 동작 확인")
    void t9() throws Exception {
        BaseLine baseLine = createBaseLine(testUser, "Test BaseLine");

        for (int i = 1; i <= 15; i++) {
            DecisionLine decisionLine = createDecisionLine(testUser, baseLine);
            createScenario(testUser, baseLine, decisionLine, "Job " + i, 100 * i, "Summary " + i);
        }

        mockMvc.perform(get("/api/v1/users/list")
                        .param("page", "1")
                        .param("size", "10")
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(10))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements").value(15))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.last").value(false));

        mockMvc.perform(get("/api/v1/users/list")
                        .param("page", "2")
                        .param("size", "10")
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(5))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    @DisplayName("성공 - 내 작성글 목록 조회 성공")
    void t10() throws Exception {
        Post post1 = createPost(testUser, "첫 번째 게시글", PostCategory.CHAT);
        createPost(testUser, "두 번째 게시글", PostCategory.POLL);
        createPost(testUser, "세 번째 게시글", PostCategory.CHAT);

        createComment(testUser, post1, "Comment 1");
        createComment(testUser, post1, "Comment 2");

        mockMvc.perform(get("/api/v1/users/my-posts")
                        .param("page", "1")
                        .param("size", "10")
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].title").value("세 번째 게시글"))
                .andExpect(jsonPath("$.items[2].commentCount").value(2))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    @DisplayName("성공 - 내 댓글 목록 조회 성공")
    void t11() throws Exception {
        User otherUser = createOtherUser();

        Post post1 = createPost(otherUser, "다른 사용자의 게시글 1", PostCategory.CHAT);
        Post post2 = createPost(otherUser, "다른 사용자의 게시글 2", PostCategory.POLL);

        createComment(testUser, post1, "첫 번째 댓글 내용");
        createComment(testUser, post2, "두 번째 댓글 내용");
        createComment(testUser, post1, "세 번째 댓글 내용");

        mockMvc.perform(get("/api/v1/users/my-comments")
                        .param("page", "1")
                        .param("size", "10")
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].content").value("세 번째 댓글 내용"))
                .andExpect(jsonPath("$.items[0].postTitle").value("다른 사용자의 게시글 1"))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    @DisplayName("성공 - 내 작성글 페이지네이션 동작 확인")
    void t12() throws Exception {
        for (int i = 1; i <= 12; i++) {
            createPost(testUser, "게시글 " + i, PostCategory.CHAT);
        }

        mockMvc.perform(get("/api/v1/users/my-posts")
                        .param("page", "1")
                        .param("size", "5")
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(5))
                .andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.last").value(false));

        mockMvc.perform(get("/api/v1/users/my-posts")
                        .param("page", "3")
                        .param("size", "5")
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    @DisplayName("성공 - 내 댓글 페이지네이션 동작 확인")
    void t13() throws Exception {
        User otherUser = createOtherUser();
        Post post = createPost(otherUser, "게시글", PostCategory.CHAT);

        for (int i = 1; i <= 15; i++) {
            createComment(testUser, post, "댓글 " + i);
        }

        mockMvc.perform(get("/api/v1/users/my-comments")
                        .param("page", "1")
                        .param("size", "10")
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(10))
                .andExpect(jsonPath("$.totalElements").value(15))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.last").value(false));

        mockMvc.perform(get("/api/v1/users/my-comments")
                        .param("page", "2")
                        .param("size", "10")
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(5))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    @DisplayName("성공 - 대표 시나리오 설정 성공")
    void t14() throws Exception {
        BaseLine baseLine = createBaseLine(testUser, "Test BaseLine");
        DecisionLine decisionLine = createDecisionLine(testUser, baseLine);
        Scenario scenario = createScenario(testUser, baseLine, decisionLine,
                "Software Engineer", 425, "대학원 진학 후 AI 연구원으로 성장");

        mockMvc.perform(put("/api/v1/users/profile-scenario")
                        .param("scenarioId", scenario.getId().toString())
                        .with(authentication(authentication))
                        .with(csrf()))
                .andExpect(status().isOk());

        Scenario updatedScenario = scenarioRepository.findById(scenario.getId()).orElseThrow();
        assert updatedScenario.isRepresentative();
    }

    @Test
    @DisplayName("성공 - 대표 시나리오 변경 성공 (기존 대표 시나리오가 있을 때)")
    void t15() throws Exception {
        BaseLine baseLine = createBaseLine(testUser, "Test BaseLine");

        DecisionLine decisionLine1 = createDecisionLine(testUser, baseLine);
        Scenario scenario1 = createScenario(testUser, baseLine, decisionLine1,
                "Designer", 350, "기존 대표 시나리오",
                ScenarioStatus.COMPLETED, true);

        DecisionLine decisionLine2 = createDecisionLine(testUser, baseLine);
        Scenario scenario2 = createScenario(testUser, baseLine, decisionLine2,
                "Software Engineer", 425, "새로운 대표 시나리오");

        mockMvc.perform(put("/api/v1/users/profile-scenario")
                        .param("scenarioId", scenario2.getId().toString())
                        .with(authentication(authentication))
                        .with(csrf()))
                .andExpect(status().isOk());

        Scenario oldRepresentative = scenarioRepository.findById(scenario1.getId()).orElseThrow();
        Scenario newRepresentative = scenarioRepository.findById(scenario2.getId()).orElseThrow();

        assert !oldRepresentative.isRepresentative();
        assert newRepresentative.isRepresentative();
    }

    @Test
    @DisplayName("성공 - 대표 프로필 조회 성공")
    void t16() throws Exception {
        BaseLine baseLine = createBaseLine(testUser, "Test BaseLine");
        DecisionLine decisionLine = createDecisionLine(testUser, baseLine);

        Scenario scenario = createScenario(testUser, baseLine, decisionLine,
                "Software Engineer", 425,
                "대학원 진학 후 AI 연구원으로 성장",
                ScenarioStatus.COMPLETED, true);

        mockMvc.perform(get("/api/v1/users/profile")
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("TestUser"))
                .andExpect(jsonPath("$.representativeScenarioId").exists())
                .andExpect(jsonPath("$.description").exists())
                .andExpect(jsonPath("$.sceneTypePoints").exists());
    }

    @Test
    @DisplayName("성공 - 대표 프로필이 없을 때 null 반환")
    void t17() throws Exception {
        mockMvc.perform(get("/api/v1/users/profile")
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").doesNotExist());
    }
}