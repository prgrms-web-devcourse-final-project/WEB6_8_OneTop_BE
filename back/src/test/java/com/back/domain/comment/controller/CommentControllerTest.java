package com.back.domain.comment.controller;

import com.back.domain.comment.dto.CommentRequest;
import com.back.domain.comment.entity.Comment;
import com.back.domain.comment.repository.CommentRepository;
import com.back.domain.post.entity.Post;
import com.back.domain.post.repository.PostRepository;
import com.back.domain.user.entity.Gender;
import com.back.domain.user.entity.Mbti;
import com.back.domain.user.entity.Role;
import com.back.domain.user.entity.User;
import com.back.domain.user.repository.UserRepository;
import com.back.global.security.CustomUserDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.UUID;

import static java.time.LocalDateTime.now;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED)
@Sql(
        statements = {
                "SET REFERENTIAL_INTEGRITY FALSE",
                "TRUNCATE TABLE COMMENTS",
                "TRUNCATE TABLE POST",
                "TRUNCATE TABLE USERS",

                "ALTER TABLE COMMENTS ALTER COLUMN id RESTART WITH 1",
                "ALTER TABLE POST ALTER COLUMN id RESTART WITH 1",
                "ALTER TABLE USERS ALTER COLUMN id RESTART WITH 1",
                "SET REFERENTIAL_INTEGRITY TRUE"
        },
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD
)
class CommentControllerTest {

    @Autowired private UserRepository userRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private CommentRepository commentRepository;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager em;

    private User testUser;
    private User anotherUser;
    private Post testPost;
    private Comment testComment;


    @BeforeEach
    void setUp() {
        String uid1 = UUID.randomUUID().toString().substring(0, 5);
        String uid2 = UUID.randomUUID().toString().substring(0, 5);

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

        anotherUser = userRepository.save(User.builder()
                .email("another" + uid2 + "@example.com")
                .nickname("anotherNick" + uid2)
                .username("another" + uid2)
                .password("password")
                .gender(Gender.F)
                .role(Role.USER)
                .mbti(Mbti.ENTP)
                .birthdayAt(LocalDateTime.of(2000, 1, 1, 0, 0))
                .build());

        // 테스트 게시글 생성
        testPost = postRepository.save(Post.builder()
                .title("테스트 게시글")
                .content("테스트 내용")
                .user(testUser)
                .build());

        // 인증 사용자 세팅
        CustomUserDetails cs = new CustomUserDetails(testUser);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(cs, null, cs.getAuthorities())
        );
    }

    @Nested
    @DisplayName("댓글 생성")
    class CreateComment {

        @Test
        @DisplayName("성공 - 정상 요청")
        void success() throws Exception {
            CommentRequest request = new CommentRequest("테스트 댓글", true);

            mockMvc.perform(post("/api/v1/posts/{postId}/comments", testPost.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.content").value("테스트 댓글"));
        }
    }

    @Nested
    @DisplayName("댓글 목록 조회")
    class GetComments {

        @Test
        @DisplayName("성공 - 기본 파라미터로 댓글 목록 조회")
        void success() throws Exception {
            createComment("테스트 댓글 1", testUser, testPost, 0, null);
            createComment("테스트 댓글 2", testUser, testPost, 0, null);
            createComment("테스트 댓글 3", testUser, testPost, 0, null);

            mockMvc.perform(get("/api/v1/posts/{postId}/comments", testPost.getId())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isArray())
                    .andExpect(jsonPath("$.items.length()").value(3))
                    .andExpect(jsonPath("$.totalElements").value(3))
                    .andExpect(jsonPath("$.totalPages").value(1));
        }

        @Test
        @DisplayName("성공 - LATEST 정렬로 댓글 목록 조회")
        void successWithLatestSort() throws Exception {
            createComment("오래된 댓글", testUser, testPost, 0, now().minusDays(2));
            createComment("중간 댓글", testUser, testPost, 0, now().minusDays(1));
            createComment("가장 최근 댓글", testUser, testPost, 0, now());

            mockMvc.perform(get("/api/v1/posts/{postId}/comments", testPost.getId())
                            .param("sortType", "LATEST")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].content").value("가장 최근 댓글"))
                    .andExpect(jsonPath("$.items[1].content").value("중간 댓글"))
                    .andExpect(jsonPath("$.items[2].content").value("오래된 댓글"));
        }

        @Test
        @DisplayName("성공 - LIKES 정렬로 댓글 목록 조회")
        void successWithLikesSort() throws Exception {
            createComment("좋아요 2개", testUser, testPost, 2, null);
            createComment("좋아요 5개", testUser, testPost, 5, null);
            createComment("좋아요 10개", testUser, testPost, 10, null);

            mockMvc.perform(get("/api/v1/posts/{postId}/comments", testPost.getId())
                            .param("sortType", "LIKES")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].likeCount").value(10))
                    .andExpect(jsonPath("$.items[1].likeCount").value(5))
                    .andExpect(jsonPath("$.items[2].likeCount").value(2));
        }

        @Test
        @DisplayName("성공 - 빈 댓글 목록 조회")
        void successWithEmptyComments() throws Exception {
            mockMvc.perform(get("/api/v1/posts/{postId}/comments", testPost.getId())
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isArray())
                    .andExpect(jsonPath("$.items.length()").value(0))
                    .andExpect(jsonPath("$.totalElements").value(0))
                    .andExpect(jsonPath("$.totalPages").value(0));
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 게시글 ID")
        void failWithNonExistentPostId() throws Exception {
            mockMvc.perform(get("/api/v1/posts/{postId}/comments", 999L)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("댓글 수정")
    class UpdateCommentTest {

        @Test
        @DisplayName("성공 - 유효한 요청으로 댓글 수정")
        void updateComment_Success() throws Exception {
            testComment = createComment("테스트 댓글", testUser, testPost, 0, null);
            CommentRequest request = new CommentRequest("수정된 댓글입니다.", false);

            mockMvc.perform(put("/api/v1/posts/{postId}/comments/{commentId}",
                            testPost.getId(),
                            testComment.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").value(testComment.getId()));

            Comment updatedComment = commentRepository.findById(testComment.getId()).orElseThrow();
            assertThat(updatedComment.getContent()).isEqualTo(request.content());
            assertThat(updatedComment.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("실패 - 빈 내용으로 댓글 수정 시도")
        void updateComment_EmptyContent_Fail() throws Exception {
            testComment = createComment("테스트 댓글", testUser, testPost, 0, null);
            CommentRequest request = new CommentRequest("", false);

            mockMvc.perform(put("/api/v1/posts/{postId}/comments/{commentId}",
                            testPost.getId(),
                            testComment.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(toJson(request)))
                    .andExpect(status().isBadRequest());

            Comment unchangedComment = commentRepository.findById(testComment.getId()).orElseThrow();
            assertThat(unchangedComment.getContent()).isEqualTo("테스트 댓글");
        }
    }

    @Nested
    @DisplayName("댓글 삭제")
    class DeleteCommentTest {

        @Test
        @DisplayName("성공 - 유효한 요청으로 댓글 삭제")
        void deleteComment_Success() throws Exception {
            testComment = createComment("테스트 댓글", testUser, testPost, 0, now());

            mockMvc.perform(delete("/api/v1/posts/{postId}/comments/{commentId}",
                            testPost.getId(),
                            testComment.getId()))
                    .andExpect(status().isOk());

            assertThat(commentRepository.findById(testComment.getId())).isEmpty();
        }

        @Test
        @DisplayName("실패 - 다른 사용자의 댓글 삭제 시도")
        void deleteComment_Unauthorized_Fail() throws Exception {
            testComment = createComment("테스트 댓글", anotherUser, testPost, 0, null);

            mockMvc.perform(delete("/api/v1/posts/{postId}/comments/{commentId}",
                            testPost.getId(),
                            testComment.getId()))
                    .andExpect(status().isUnauthorized());

            assertThat(commentRepository.findById(testComment.getId())).isPresent();
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 댓글 삭제 시도")
        void deleteComment_CommentNotFound_Fail() throws Exception {
            testComment = createComment("테스트 댓글", testUser, testPost, 0, null);

            mockMvc.perform(delete("/api/v1/posts/{postId}/comments/{commentId}",
                            testPost.getId(),
                            999L))
                    .andExpect(status().isNotFound());
        }
    }

    private Comment createComment(String content, User user, Post post, int likeCount, LocalDateTime createdDate) {
        Comment comment = Comment.builder()
                .content(content)
                .user(user)
                .post(post)
                .hide(false)
                .likeCount(likeCount)
                .build();
        if (createdDate != null) {
            Field createdDateField = ReflectionUtils.findField(Comment.class, "createdDate");
            ReflectionUtils.makeAccessible(createdDateField);
            ReflectionUtils.setField(createdDateField, comment, createdDate);
        }
        return commentRepository.save(comment);
    }

    private String toJson(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }
}

