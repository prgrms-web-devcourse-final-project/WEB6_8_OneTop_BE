package com.back.domain.poll.controller;

import com.back.domain.poll.entity.PollVote;
import com.back.domain.poll.repository.PollVoteRepository;
import com.back.domain.post.entity.Post;
import com.back.domain.post.enums.PostCategory;
import com.back.domain.post.repository.PostRepository;
import com.back.domain.user.entity.Gender;
import com.back.domain.user.entity.Mbti;
import com.back.domain.user.entity.Role;
import com.back.domain.user.entity.User;
import com.back.domain.user.repository.UserRepository;
import com.back.global.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED)
@Sql(
        statements = {
                "SET REFERENTIAL_INTEGRITY FALSE",
                "TRUNCATE TABLE POLL_VOTES",
                "TRUNCATE TABLE COMMENTS",
                "TRUNCATE TABLE POST",
                "TRUNCATE TABLE USERS",
                "ALTER TABLE POLL_VOTES ALTER COLUMN ID RESTART WITH 1",
                "ALTER TABLE COMMENTS ALTER COLUMN ID RESTART WITH 1",
                "ALTER TABLE POST ALTER COLUMN ID RESTART WITH 1",
                "ALTER TABLE USERS ALTER COLUMN ID RESTART WITH 1",
                "SET REFERENTIAL_INTEGRITY TRUE"
        },
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD
)
class PollVoteControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PollVoteRepository pollVoteRepository;

    private User testUser;
    private Post pollPost;

    @BeforeEach
    void setUp() {
        String uid1 = UUID.randomUUID().toString().substring(0, 5);

        testUser = userRepository.save(User.builder()
                .email("testuser" + uid1 + "@example.com")
                .nickname("nickname" + uid1)
                .username("tester" + uid1)
                .password("password")
                .gender(Gender.M)
                .role(Role.USER)
                .mbti(Mbti.ISFJ)
                .birthdayAt(LocalDateTime.of(2000, 1, 1, 0, 0))
                .build());

        userRepository.save(testUser);

        String voteContent = """
                {
                    "pollUid": "11111111-1111-1111-1111-111111111111",
                    "options": [
                        {"index":1,"text":"첫 번째 옵션"},
                        {"index":2,"text":"두 번째 옵션"},
                        {"index":3,"text":"세 번째 옵션"}
                    ]
                }
                """;

        pollPost = postRepository.save(Post.builder()
                .title("테스트 투표 게시글")
                .content("내용")
                .category(PostCategory.POLL)
                .user(testUser)
                .voteContent(voteContent)
                .build());

        CustomUserDetails cs = new CustomUserDetails(testUser);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(cs, null, cs.getAuthorities())
        );
    }

    @Nested
    @DisplayName("투표 참여")
    class participateVote {

        @Test
        @DisplayName("성공 - 투표하기")
        void vote_post_success() throws Exception {
            String requestJson = """
                    {
                        "choice": [1, 2]
                    }
                    """;

            mockMvc.perform(post("/api/v1/posts/{postId}/polls", pollPost.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk());

            List<PollVote> votes = pollVoteRepository.findByPostId(pollPost.getId());
            assertThat(votes).hasSize(1);
            assertThat(votes.getFirst().getUser().getId()).isEqualTo(testUser.getId());
        }

        @Test
        @DisplayName("실패 - 중복 투표 방지")
        void vote_post_duplicate_fail() throws Exception {
            String firstRequest = """
                {
                    "choice": [1, 2]
                }
                """;

            mockMvc.perform(post("/api/v1/posts/{postId}/polls", pollPost.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(firstRequest))
                    .andExpect(status().isOk());

            String secondRequest = """
                {
                    "choice": [1]
                }
                """;

            mockMvc.perform(post("/api/v1/posts/{postId}/polls", pollPost.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(secondRequest))
                    .andExpect(status().isBadRequest());

            List<PollVote> votes = pollVoteRepository.findByPostId(pollPost.getId());
            assertThat(votes).hasSize(1);
        }
    }

    @Nested
    @DisplayName("투표 조회")
    class getVote {

        @Test
        @DisplayName("성공 - 여러 사용자가 투표한 후 집계 결과 조회")
        void get_vote_with_multiple_participants_success() throws Exception {
            // given: 첫 번째 사용자 투표
            String firstVote = """
                    {
                        "choice": [1]
                    }
                    """;

            mockMvc.perform(post("/api/v1/posts/{postId}/polls", pollPost.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(firstVote))
                    .andExpect(status().isOk());

            // given: 두 번째 사용자 생성 및 투표
            String uid2 = UUID.randomUUID().toString().substring(0, 5);
            User secondUser = userRepository.save(User.builder()
                    .email("testuser" + uid2 + "@example.com")
                    .nickname("nickname" + uid2)
                    .username("tester" + uid2)
                    .password("password")
                    .gender(Gender.M)
                    .role(Role.USER)
                    .mbti(Mbti.ISFJ)
                    .birthdayAt(LocalDateTime.of(2000, 1, 1, 0, 0))
                    .build());

            CustomUserDetails secondUserDetails = new CustomUserDetails(secondUser);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(secondUserDetails, null, secondUserDetails.getAuthorities())
            );

            String secondVote = """
                    {
                        "choice": [1, 2]
                    }
                    """;

            mockMvc.perform(post("/api/v1/posts/{postId}/polls", pollPost.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(secondVote))
                    .andExpect(status().isOk());

            // when & then: 집계된 투표 결과 조회
            mockMvc.perform(get("/api/v1/posts/{postId}/polls", pollPost.getId())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.options[0].voteCount").value(2))
                    .andExpect(jsonPath("$.options[1].voteCount").value(1))
                    .andExpect(jsonPath("$.options[2].voteCount").value(0));
        }
    }
}
