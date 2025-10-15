package com.back.domain.like.controller;

import com.back.domain.comment.entity.Comment;
import com.back.domain.comment.repository.CommentRepository;
import com.back.domain.like.entity.CommentLike;
import com.back.domain.like.entity.PostLike;
import com.back.domain.like.repository.CommentLikeRepository;
import com.back.domain.like.repository.PostLikeRepository;
import com.back.domain.post.entity.Post;
import com.back.domain.post.repository.PostRepository;
import com.back.domain.user.entity.Gender;
import com.back.domain.user.entity.Mbti;
import com.back.domain.user.entity.Role;
import com.back.domain.user.entity.User;
import com.back.domain.user.repository.UserRepository;
import com.back.global.exception.ErrorCode;
import com.back.global.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED)
@Sql(
        statements = {
                "TRUNCATE TABLE public.comment_likes, public.comments, public.post_likes, public.post, public.users RESTART IDENTITY CASCADE"
        },
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD
)
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class LikeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostLikeRepository postLikeRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private CommentLikeRepository commentLikeRepository;

    static final int THREAD_POOL_SIZE = 50;
    static final int CONCURRENT_USERS = 50;

    private Post testPost;
    private User testUser;
    private Comment testComment;

    @BeforeEach
    void setUp() {
        testUser = createTestUser("testuser");
        testPost = createTestPost(testUser);
        testComment = createTestComment(testPost, testUser);

        setAuthentication(testUser);
    }

    @Nested
    @DisplayName("좋아요 등록 및 취소")
    class LikeFeatureTest {

        @Test
        @DisplayName("성공 - 50명 동시 좋아요 등록")
        void addLikeConcurrent() throws InterruptedException {
            List<User> testUsers = IntStream.rangeClosed(1, CONCURRENT_USERS)
                    .mapToObj(i -> createTestUser("concurrent" + i))
                    .collect(Collectors.toList());

            userRepository.saveAll(testUsers);
            userRepository.flush();

            try (ExecutorService es = Executors.newFixedThreadPool(THREAD_POOL_SIZE)) {
                List<Callable<Void>> tasks = testUsers.stream()
                        .map(user -> (Callable<Void>) () -> {
                            performLike(user, testPost.getId());
                            return null;
                        })
                        .toList();

                es.invokeAll(tasks);
            }

            Post post = postRepository.findById(testPost.getId()).orElseThrow();
            assertEquals(CONCURRENT_USERS, post.getLikeCount());
        }

        @Test
        @DisplayName("실패 - 이미 좋아요를 누른 유저가 다시 좋아요 등록 시 예외")
        void addLikeAlreadyLiked() throws Exception {
            postLikeRepository.save(PostLike.builder().user(testUser).post(testPost).build());
            postLikeRepository.flush();

            mockMvc.perform(post("/api/v1/posts/{postId}/likes", testPost.getId()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.POST_ALREADY_LIKED.getCode()))
                    .andExpect(jsonPath("$.message").value(ErrorCode.POST_ALREADY_LIKED.getMessage()));
        }

        @Test
        @DisplayName("성공 - 좋아요 취소")
        void removeLike() throws Exception {
            postLikeRepository.save(PostLike.builder().user(testUser).post(testPost).build());
            testPost.incrementLikeCount();
            postRepository.save(testPost);
            postRepository.flush();

            mockMvc.perform(delete("/api/v1/posts/{postId}/likes", testPost.getId()))
                    .andExpect(status().isOk());

            Post post = postRepository.findById(testPost.getId()).orElseThrow();
            assertEquals(0, post.getLikeCount());
        }
    }

    @Nested
    @DisplayName("댓글 좋아요 등록 및 취소")
    class CommentLikeFeatureTest {

        @Test
        @DisplayName("성공 - 50명 동시 댓글 좋아요 등록")
        void addCommentLikeConcurrent() throws InterruptedException {
            List<User> testUsers = IntStream.rangeClosed(1, CONCURRENT_USERS)
                    .mapToObj(i -> createTestUser("concurrent" + i))
                    .collect(Collectors.toList());

            userRepository.saveAll(testUsers);
            userRepository.flush();

            try (ExecutorService es = Executors.newFixedThreadPool(THREAD_POOL_SIZE)) {
                List<Callable<Void>> tasks = testUsers.stream()
                        .map(user -> (Callable<Void>) () -> {
                            performCommentLike(user, testPost.getId(), testComment.getId());
                            return null;
                        })
                        .toList();

                es.invokeAll(tasks);
            }

            Comment comment = commentRepository.findById(testComment.getId()).orElseThrow();
            assertEquals(CONCURRENT_USERS, comment.getLikeCount());
        }

        @Test
        @DisplayName("실패 - 이미 좋아요 누른 유저가 다시 좋아요 등록 시 예외")
        void addCommentLikeAlreadyLiked() throws Exception {
            commentLikeRepository.save(CommentLike.builder().user(testUser).comment(testComment).build());
            commentLikeRepository.flush();

            mockMvc.perform(post("/api/v1/posts/{postId}/comments/{commentId}/likes", testPost.getId(), testComment.getId()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.COMMENT_ALREADY_LIKED.getCode()))
                    .andExpect(jsonPath("$.message").value(ErrorCode.COMMENT_ALREADY_LIKED.getMessage()));
        }

        @Test
        @DisplayName("성공 - 댓글 좋아요 취소")
        void removeCommentLike() throws Exception {
            commentLikeRepository.save(CommentLike.builder().user(testUser).comment(testComment).build());
            testComment.incrementLikeCount();
            commentRepository.save(testComment);
            commentRepository.flush();

            mockMvc.perform(delete("/api/v1/posts/{postId}/comments/{commentId}/likes", testPost.getId(), testComment.getId()))
                    .andExpect(status().isOk());

            Comment comment = commentRepository.findById(testComment.getId()).orElseThrow();
            assertEquals(0, comment.getLikeCount());
        }
    }

    /**
     * 테스트에서 사용할 공통 메서드
     */
    private User createTestUser(String prefix) {
        String uuid = UUID.randomUUID().toString().substring(0, 5);
        return userRepository.save(User.builder()
                .email(prefix + uuid + "@example.com")
                .password("password")
                .username(prefix)
                .nickname(prefix + "_" + uuid)
                .gender(Gender.M)
                .role(Role.USER)
                .mbti(Mbti.ISFJ)
                .birthdayAt(LocalDateTime.of(2000, 1, 1, 0, 0))
                .beliefs("도전")
                .build());
    }

    private Post createTestPost(User user) {
        return postRepository.save(Post.builder()
                .title("테스트 게시글")
                .content("테스트 내용")
                .user(user)
                .build());
    }

    private void setAuthentication(User user) {
        CustomUserDetails cs = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(cs, null, cs.getAuthorities())
        );
    }

    private Comment createTestComment(Post post, User user) {
        return commentRepository.save(Comment.builder()
                .post(post)
                .user(user)
                .content("테스트 댓글")
                .build());
    }

    private void performLike(User user, Long postId) throws Exception {
        setAuthentication(user);
        mockMvc.perform(post("/api/v1/posts/{postId}/likes", postId))
                .andExpect(status().isOk());
        SecurityContextHolder.clearContext();
    }

    private void performCommentLike(User user, Long postId, Long commentId) throws Exception {
        setAuthentication(user);
        mockMvc.perform(post("/api/v1/posts/{postId}/comments/{commentId}/likes", postId, commentId))
                .andExpect(status().isOk());
        SecurityContextHolder.clearContext();
    }
}
