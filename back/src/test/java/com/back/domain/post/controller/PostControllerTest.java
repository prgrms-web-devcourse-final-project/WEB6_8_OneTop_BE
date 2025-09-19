package com.back.domain.post.controller;

import com.back.domain.post.dto.PostRequest;
import com.back.domain.post.dto.PostResponse;
import com.back.domain.post.entity.Post;
import com.back.domain.post.enums.PostCategory;
import com.back.domain.post.repository.PostRepository;
import com.back.domain.user.entity.Gender;
import com.back.domain.user.entity.Mbti;
import com.back.domain.user.entity.Role;
import com.back.domain.user.entity.User;
import com.back.domain.user.repository.UserRepository;
import com.back.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        User testUser = User.builder()
                .loginId("testLoginId")
                .email("test@example.com")
                .password("testPassword")
                .beliefs("도전")
                .gender(Gender.M)
                .role(Role.USER)
                .mbti(Mbti.ISFJ)
                .birthdayAt(LocalDateTime.of(2000, 3, 1, 0, 0))
                .build();
        userRepository.save(testUser);

        IntStream.rangeClosed(1, 5).forEach(i -> {
            postRepository.save(
                    Post.builder()
                            .title("목록 게시글 " + i)
                            .content("목록 내용 " + i)
                            .category(PostCategory.CHAT)
                            .user(testUser)
                            .build()
            );
        });
    }

    @Nested
    @DisplayName("게시글 생성")
    class CreatePost {

        @Test
        @DisplayName("성공 - 정상 요청")
        void success() throws Exception {
            // given
            PostRequest request = new PostRequest("테스트 게시글", "테스트 내용입니다.", PostCategory.CHAT);

            // when
            MvcResult result = mockMvc.perform(post("/api/v1/posts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.title").value("테스트 게시글"))
                    .andExpect(jsonPath("$.content").value("테스트 내용입니다."))
                    .andExpect(jsonPath("$.category").value("CHAT"))
                    .andReturn();

            String responseBody = result.getResponse().getContentAsString();
            PostResponse response = objectMapper.readValue(responseBody, PostResponse.class);

            Post savedPost = postRepository.findById(response.id()).orElseThrow();
            assertThat(savedPost.getTitle()).isEqualTo("테스트 게시글");
            assertThat(savedPost.getContent()).isEqualTo("테스트 내용입니다.");
            assertThat(savedPost.getCategory()).isEqualTo(PostCategory.CHAT);
        }

        @Test
        @DisplayName("실패 - 유효성 검사 실패")
        void fail_ValidationError() throws Exception {
            // given
            PostRequest request = new PostRequest("", "테스트 내용입니다.", PostCategory.CHAT);

            // when & then
            mockMvc.perform(post("/api/v1/posts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()))
                    .andExpect(jsonPath("$.message").exists());
        }
    }

    @Nested
    @DisplayName("게시글 조회")
    class GetPost {

        @Test
        @DisplayName("성공 - 존재하는 게시글 조회")
        void success() throws Exception {
            // given
            Post savedPost = postRepository.save(
                    Post.builder()
                            .title("조회 테스트 게시글")
                            .content("조회 테스트 내용입니다.")
                            .category(PostCategory.CHAT)
                            .user(userRepository.findAll().get(0))
                            .build()
            );

            // when & then
            mockMvc.perform(get("/api/v1/posts/{postId}", savedPost.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("조회 테스트 게시글"))
                    .andExpect(jsonPath("$.content").value("조회 테스트 내용입니다."))
                    .andExpect(jsonPath("$.category").value("CHAT"));
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 게시글 ID")
        void fail_NotFound() throws Exception {
            // when & then
            mockMvc.perform(get("/api/v1/posts/{postId}", 9999L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                    .andExpect(jsonPath("$.code").value(ErrorCode.POST_NOT_FOUND.getCode()))
                    .andExpect(jsonPath("$.message").value(ErrorCode.POST_NOT_FOUND.getMessage()))
                    .andExpect(jsonPath("$.path").value("/api/v1/posts/9999"));
        }
    }

    @Nested
    @DisplayName("게시글 목록 조회")
    class GetPosts {

        @Test
        @DisplayName("성공 - 게시글 목록 조회")
        void success() throws Exception {
            // when & then
            mockMvc.perform(get("/api/v1/posts"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(5))
                    .andExpect(jsonPath("$[0].title").value("목록 게시글 1"))
                    .andExpect(jsonPath("$[1].title").value("목록 게시글 2"));
        }
    }
}