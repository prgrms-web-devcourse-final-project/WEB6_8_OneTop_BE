package com.back.domain.comment.controller;

import com.back.domain.comment.dto.CommentRequest;
import com.back.domain.comment.entity.Comment;
import com.back.domain.comment.repository.CommentRepository;
import com.back.domain.post.entity.Post;
import com.back.domain.post.fixture.PostFixture;
import com.back.domain.post.repository.PostRepository;
import com.back.domain.user.entity.Gender;
import com.back.domain.user.entity.Mbti;
import com.back.domain.user.entity.Role;
import com.back.domain.user.entity.User;
import com.back.domain.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class CommentControllerTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private User testUser;
    private User anotherUser;
    private Post testPost;
    private Comment testComment;

    @BeforeEach
    void setUp() {
        PostFixture postFixture = new PostFixture(userRepository, postRepository);
        User user = postFixture.createTestUser();
        testUser = userRepository.save(user);

        Post post = Post.builder()
                .title("테스트 게시글")
                .content("테스트 내용")
                .user(user)
                .build();
        testPost = postRepository.save(post);

        User another = postFixture.createAnotherUser();
        anotherUser = userRepository.save(another);
    }

    @Nested
    @DisplayName("댓글 생성")
    class CreateComment {

        @Test
        @DisplayName("성공 - 정상 요청")
        void success() throws Exception {
            CommentRequest request = new CommentRequest("테스트 댓글", true);

            mockMvc.perform(post("/api/v1/posts/{postId}/comments", testPost.getId())
                            .param("userId", String.valueOf(testUser.getId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").value("테스트 댓글"))
                    .andExpect(jsonPath("$.message").value("성공적으로 생성되었습니다."))
                    .andDo(print());
        }
    }

    @Nested
    @DisplayName("댓글 목록 조회")
    class GetComments {

        @Test
        @DisplayName("성공 - 기본 파라미터로 댓글 목록 조회")
        void success() throws Exception {
            // Given - 테스트 댓글들 생성
            createTestComments();

            // When & Then
            mockMvc.perform(get("/api/v1/posts/{postId}/comments", testPost.getId())
                            .param("userId", String.valueOf(testUser.getId()))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("성공적으로 조회되었습니다."))
                    .andExpect(jsonPath("$.data.items").isArray())
                    .andExpect(jsonPath("$.data.items.length()").value(3))
                    .andExpect(jsonPath("$.data.totalElements").value(3))
                    .andExpect(jsonPath("$.data.totalPages").value(1))
                    .andDo(print());
        }

        @Test
        @DisplayName("성공 - LATEST 정렬로 댓글 목록 조회")
        void successWithLatestSort() throws Exception {
            // Given
            createTestCommentsWithDifferentTimes();

            // When & Then
            mockMvc.perform(get("/api/v1/posts/{postId}/comments", testPost.getId())
                            .param("userId", String.valueOf(testUser.getId()))
                            .param("sortType", "LATEST")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[0].content").value("가장 최근 댓글"))
                    .andExpect(jsonPath("$.data.items[1].content").value("중간 댓글"))
                    .andExpect(jsonPath("$.data.items[2].content").value("오래된 댓글"))
                    .andDo(print());
        }

        @Test
        @DisplayName("성공 - LIKES 정렬로 댓글 목록 조회")
        void successWithLikesSort() throws Exception {
            // Given
            createTestCommentsWithDifferentLikes();

            // When & Then
            mockMvc.perform(get("/api/v1/posts/{postId}/comments", testPost.getId())
                            .param("userId", String.valueOf(testUser.getId()))
                            .param("sortType", "LIKES")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[0].likeCount").value(10))
                    .andExpect(jsonPath("$.data.items[1].likeCount").value(5))
                    .andExpect(jsonPath("$.data.items[2].likeCount").value(2))
                    .andDo(print());
        }

        @Test
        @DisplayName("성공 - 빈 댓글 목록 조회")
        void successWithEmptyComments() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/v1/posts/{postId}/comments", testPost.getId())
                            .param("userId", String.valueOf(testUser.getId()))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items").isArray())
                    .andExpect(jsonPath("$.data.items.length()").value(0))
                    .andExpect(jsonPath("$.data.totalElements").value(0))
                    .andExpect(jsonPath("$.data.totalPages").value(0))
                    .andDo(print());
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 게시글 ID")
        void failWithNonExistentPostId() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/v1/posts/{postId}/comments", 999L)
                            .param("userId", String.valueOf(testUser.getId()))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andDo(print());
        }

    }

    @Nested
    @DisplayName("댓글 수정")
    class UpdateCommentTest {

        @Test
        @DisplayName("성공 - 유효한 요청으로 댓글 수정")
        void updateComment_Success() throws Exception {
            // Given
            testComment = Comment.builder()
                    .content("테스트 댓글")
                    .user(testUser)
                    .post(testPost)
                    .build();
            testComment = commentRepository.save(testComment);
            CommentRequest request = new CommentRequest("수정된 댓글입니다.", false);

            // When & Then
            mockMvc.perform(put("/api/v1/posts/{postId}/comments/{commentId}",
                            testPost.getId(),
                            testComment.getId())
                            .param("userId", testUser.getId().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("성공적으로 수정되었습니다."))
                    .andExpect(jsonPath("$.data").value(testComment.getId()))
                    .andDo(print());

            Comment updatedComment = commentRepository.findById(testComment.getId()).orElseThrow();
            assertThat(updatedComment.getContent()).isEqualTo(request.content());
            assertThat(updatedComment.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("실패 - 빈 내용으로 댓글 수정 시도")
        void updateComment_EmptyContent_Fail() throws Exception {
            // Given
            testComment = Comment.builder()
                    .content("테스트 댓글")
                    .user(testUser)
                    .post(testPost)
                    .build();
            testComment = commentRepository.save(testComment);
            CommentRequest request = new CommentRequest("", false);

            // When & Then
            mockMvc.perform(put("/api/v1/posts/{postId}/comments/{commentId}",
                            testPost.getId(),
                            testComment.getId())
                            .param("userId", testUser.getId().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andDo(print());

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
            testComment = Comment.builder()
                    .content("테스트 댓글")
                    .user(testUser)
                    .post(testPost)
                    .build();
            testComment = commentRepository.save(testComment);

            // When & Then
            mockMvc.perform(delete("/api/v1/posts/{postId}/comments/{commentId}",
                            testPost.getId(),
                            testComment.getId())
                            .param("userId", testUser.getId().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("성공적으로 삭제되었습니다."))
                    .andDo(print());

            // 실제 DB에서 삭제 확인
            Optional<Comment> deletedComment = commentRepository.findById(testComment.getId());
            assertThat(deletedComment).isEmpty();
        }

        @Test
        @DisplayName("실패 - 다른 사용자의 댓글 삭제 시도")
        void deleteComment_Unauthorized_Fail() throws Exception {
            testComment = Comment.builder()
                    .content("테스트 댓글")
                    .user(testUser)
                    .post(testPost)
                    .build();
            testComment = commentRepository.save(testComment);

            // When & Then
            mockMvc.perform(delete("/api/v1/posts/{postId}/comments/{commentId}",
                            testPost.getId(),
                            testComment.getId())
                            .param("userId", anotherUser.getId().toString()))
                    .andExpect(status().isUnauthorized())
                    .andDo(print());

            Optional<Comment> existingComment = commentRepository.findById(testComment.getId());
            assertThat(existingComment).isPresent();
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 댓글 삭제 시도")
        void deleteComment_CommentNotFound_Fail() throws Exception {
            testComment = Comment.builder()
                    .content("테스트 댓글")
                    .user(testUser)
                    .post(testPost)
                    .build();
            testComment = commentRepository.save(testComment);

            // When & Then
            mockMvc.perform(delete("/api/v1/posts/{postId}/comments/{commentId}",
                            testPost.getId(),
                            999L)
                            .param("userId", testUser.getId().toString()))
                    .andExpect(status().isNotFound())
                    .andDo(print());
        }
    }

    private void createTestComments() {
        for (int i = 1; i <= 3; i++) {
            Comment comment = Comment.builder()
                    .content("테스트 댓글 " + i)
                    .post(testPost)
                    .user(testUser)
                    .hide(false)
                    .build();
            commentRepository.save(comment);
        }
    }

    private void createTestCommentsWithDifferentTimes() {
        // 오래된 댓글
        Comment oldComment = Comment.builder()
                .content("오래된 댓글")
                .post(testPost)
                .user(testUser)
                .hide(false)
                .build();
        Field createdDateOldField = ReflectionUtils.findField(oldComment.getClass(), "createdDate");
        ReflectionUtils.makeAccessible(createdDateOldField);
        ReflectionUtils.setField(createdDateOldField, oldComment, LocalDateTime.now().minusDays(1));
        commentRepository.save(oldComment);

        // 중간 댓글
        Comment middleComment = Comment.builder()
                .content("중간 댓글")
                .post(testPost)
                .user(testUser)
                .hide(false)
                .build();

        Field createdDateField = ReflectionUtils.findField(middleComment.getClass(), "createdDate");
        ReflectionUtils.makeAccessible(createdDateField);
        ReflectionUtils.setField(createdDateField, middleComment, LocalDateTime.now().minusDays(1));
        commentRepository.save(middleComment);

        // 최근 댓글
        Comment recentComment = Comment.builder()
                .content("가장 최근 댓글")
                .post(testPost)
                .user(testUser)
                .hide(false)
                .build();
        commentRepository.save(recentComment);
    }

    private void createTestCommentsWithDifferentLikes() {
        Comment comment1 = Comment.builder()
                .content("좋아요 2개")
                .post(testPost)
                .user(testUser)
                .hide(false)
                .likeCount(2)
                .build();
        commentRepository.save(comment1);

        Comment comment2 = Comment.builder()
                .content("좋아요 5개")
                .post(testPost)
                .user(testUser)
                .hide(false)
                .likeCount(5)
                .build();
        commentRepository.save(comment2);

        Comment comment3 = Comment.builder()
                .content("좋아요 10개")
                .post(testPost)
                .user(testUser)
                .hide(false)
                .likeCount(10)
                .build();
        commentRepository.save(comment3);
    }
}