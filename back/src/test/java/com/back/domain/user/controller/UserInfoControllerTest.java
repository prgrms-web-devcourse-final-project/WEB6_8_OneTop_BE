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

    @Test
    @DisplayName("성공 - 사용자 통계 정보 조회 성공")
    void t1() throws Exception {
        BaseLine testBaseLine = BaseLine.builder()
                .user(testUser)
                .title("Test BaseLine")
                .build();
        testBaseLine = baseLineRepository.save(testBaseLine);

        DecisionLine testDecisionLine = DecisionLine.builder()
                .user(testUser)
                .baseLine(testBaseLine)
                .status(DecisionLineStatus.COMPLETED)
                .build();
        testDecisionLine = decisionLineRepository.save(testDecisionLine);

        Scenario scenario = Scenario.builder()
                .user(testUser)
                .decisionLine(testDecisionLine)
                .status(ScenarioStatus.COMPLETED)
                .job("Software Engineer")
                .total(100)
                .summary("Test summary 1")
                .description("Test description 1")
                .build();
        scenarioRepository.save(scenario);

        Post post = Post.builder()
                .user(testUser)
                .title("Test Post 1")
                .content("Content 1")
                .category(PostCategory.CHAT)
                .hide(false)
                .build();
        postRepository.save(post);

        Comment comment = Comment.builder()
                .user(testUser)
                .post(post)
                .content("Comment 1")
                .hide(false)
                .build();
        commentRepository.save(comment);

        mockMvc.perform(get("/api/v1/users-info/stats")
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
}