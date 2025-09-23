package com.back.domain.like.controller;

import com.back.domain.like.entity.PostLike;
import com.back.domain.like.repository.PostLikeRepository;
import com.back.domain.post.entity.Post;
import com.back.domain.post.fixture.PostFixture;
import com.back.domain.post.repository.PostRepository;
import com.back.domain.user.entity.Gender;
import com.back.domain.user.entity.Mbti;
import com.back.domain.user.entity.Role;
import com.back.domain.user.entity.User;
import com.back.domain.user.repository.UserRepository;
import com.back.global.exception.ErrorCode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
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

    static final int THREAD_POOL_SIZE = 50;
    static final int CONCURRENT_USERS = 50;

    private Post testPost;
    private User testUser;
    private List<Long> userIds;

    @BeforeEach
    void setUp() {
        PostFixture fixture = new PostFixture(userRepository, postRepository);
        testUser = fixture.createTestUser();
        testPost = fixture.createPostForDetail(testUser);

        userRepository.flush();
        postRepository.flush();
    }

    @AfterEach
    void tearDown() {
        postLikeRepository.deleteAll();
        postRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Nested
    @DisplayName("좋아요 등록 및 취소")
    class LikeFeatureTest {

        @Test
        @DisplayName("성공 - 50명 동시 좋아요 등록")
        void AddLikeConcurrent() throws InterruptedException {
            List<User> testUsers = IntStream.rangeClosed(1, CONCURRENT_USERS)
                    .mapToObj(i -> User.builder()
                            .loginId("loginId" + i)
                            .email("test" + i + "@example.com")
                            .password("password")
                            .nickname("nickname" + i)
                            .beliefs("도전")
                            .gender(Gender.M)
                            .role(Role.USER)
                            .mbti(Mbti.ISFJ)
                            .birthdayAt(LocalDateTime.of(2000, 1, 1, 0, 0))
                            .build())
                    .collect(Collectors.toList());

            userRepository.saveAll(testUsers);
            userRepository.flush();
            userIds = testUsers.stream()
                    .map(User::getId)
                    .toList();

            try (ExecutorService es = Executors.newFixedThreadPool(THREAD_POOL_SIZE)) {
                List<Callable<Void>> tasks = new ArrayList<>();
                for (Long userId : userIds) {
                    tasks.add(new LikeTask<>(mockMvc, testPost.getId(), userId));
                }

                es.invokeAll(tasks);
            }

            Post post = postRepository.findById(testPost.getId()).orElseThrow();
            assertEquals(CONCURRENT_USERS, post.getLikeCount());
        }

        @Test
        @DisplayName("실패 - 이미 좋아요를 누른 유저가 다시 좋아요 등록 시 예외")
        void AddLikeAlreadyLiked() throws Exception {
            PostLike pl = PostLike.builder()
                    .user(testUser)
                    .post(testPost)
                    .build();
            postLikeRepository.save(pl);
            postLikeRepository.flush();

            mockMvc.perform(post("/api/v1/posts/{postId}/likes", testPost.getId())
                            .param("userId", String.valueOf(testUser.getId())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.POST_ALREADY_LIKED.getCode()))
                    .andExpect(jsonPath("$.message").value(ErrorCode.POST_ALREADY_LIKED.getMessage()))
                    .andDo(print());
        }

        @Test
        @DisplayName("성공 - 좋아요 취소")
        void RemoveLike() throws Exception {
            User savedUser = userRepository.findById(testUser.getId()).orElseThrow();
            Post savedPost = postRepository.findById(testPost.getId()).orElseThrow();

            PostLike pl = PostLike.builder()
                    .user(savedUser)
                    .post(savedPost)
                    .build();
            postLikeRepository.save(pl);

            savedPost.incrementLikeCount();
            postRepository.save(savedPost);
            postRepository.flush();

            mockMvc.perform(delete("/api/v1/posts/{postId}/likes", testPost.getId())
                            .param("userId", String.valueOf(testUser.getId())))
                    .andExpect(status().isOk())
                    .andDo(print());

            postRepository.flush();
            Post post = postRepository.findById(testPost.getId()).orElseThrow();
            assertEquals(0, post.getLikeCount());
        }
    }

    static class LikeTask<T> implements Callable<T> {
        private final MockMvc mockMvc;
        private final Long postId;
        private final Long userId;

        LikeTask(MockMvc mockMvc, Long postId, Long userId) {
            this.mockMvc = mockMvc;
            this.postId = postId;
            this.userId = userId;
        }

        @Override
        public T call() throws Exception {
            mockMvc.perform(post("/api/v1/posts/{postId}/likes", postId)
                            .param("userId", String.valueOf(userId)))
                    .andExpect(status().isOk());
            return null;
        }
    }
}