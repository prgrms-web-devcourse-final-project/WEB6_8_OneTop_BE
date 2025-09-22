package com.back.domain.post.controller;

import com.back.domain.post.dto.PostRequest;
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
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.stream.IntStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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

    private User testUser;
    private User anotherUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
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

        anotherUser = User.builder()
                .loginId("anotherLoginId")
                .email("another@example.com")
                .password("another")
                .beliefs("도전")
                .gender(Gender.F)
                .role(Role.USER)
                .mbti(Mbti.ISFJ)
                .birthdayAt(LocalDateTime.of(2001, 4, 1, 0, 0))
                .build();
        userRepository.save(anotherUser);

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
            mockMvc.perform(post("/api/v1/posts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.title").value("테스트 게시글"))
                    .andExpect(jsonPath("$.data.content").value("테스트 내용입니다."))
                    .andExpect(jsonPath("$.data.category").value("CHAT"))
                    .andExpect(jsonPath("$.message").value("성공적으로 생성되었습니다."))
                    .andExpect(jsonPath("$.status").value(200));
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
                    .andExpect(jsonPath("$.data.title").value("조회 테스트 게시글"))
                    .andExpect(jsonPath("$.data.content").value("조회 테스트 내용입니다."))
                    .andExpect(jsonPath("$.data.category").value("CHAT"))
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.message").value("성공적으로 조회되었습니다."));
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
        @DisplayName("성공 - 페이징 파라미터가 없는 경우")
        void successWithDefaultParameters() throws Exception {
            // when & then
            mockMvc.perform(get("/api/v1/posts"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.page").value(1))
                    .andExpect(jsonPath("$.data.size").value(5))
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.message").value("성공적으로 조회되었습니다."));
        }

        @Test
        @DisplayName("성공 - page와 size 모두 지정")
        void successWithBothParameters() throws Exception {
            // when & then
            mockMvc.perform(get("/api/v1/posts")
                            .param("page", "1")
                            .param("size", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items.length()").value(5))
                    .andExpect(jsonPath("$.data.items[0].title").value("목록 게시글 1"))
                    .andExpect(jsonPath("$.data.items[1].title").value("목록 게시글 2"))
                    .andExpect(jsonPath("$.data.page").value(1))
                    .andExpect(jsonPath("$.data.size").value(5))
                    .andExpect(jsonPath("$.data.totalElements").value(5))
                    .andExpect(jsonPath("$.data.totalPages").value(1))
                    .andExpect(jsonPath("$.data.last").value(true))
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.message").value("성공적으로 조회되었습니다."));
        }

        @Test
        @DisplayName("성공 - 정렬 파라미터 포함")
        void successWithSortParameters() throws Exception {
            // when & then
            mockMvc.perform(get("/api/v1/posts")
                            .param("page", "1")
                            .param("size", "5")
                            .param("sort", "createdDate,desc")
                            .param("sort", "title,asc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.page").value(1))
                    .andExpect(jsonPath("$.data.size").value(5))
                    .andExpect(jsonPath("$.status").value(200));
        }
    }

    @Nested
    @DisplayName("게시글 수정")
    class UpdatePost {

        @Test
        @DisplayName("성공 - 본인 게시글 수정")
        @Sql(statements = {
                "UPDATE users SET id = 1 WHERE login_id = 'testLoginId'"
        })
        void success() throws Exception {
            // given - ID=1인 사용자의 게시글 생성
            User user1 = userRepository.findById(1L).orElseThrow();
            Post savedPost = postRepository.save(
                    Post.builder()
                            .title("수정 전 제목")
                            .content("수정 전 내용")
                            .category(PostCategory.CHAT)
                            .user(user1)
                            .build()
            );

            PostRequest updateRequest = new PostRequest("수정된 제목", "수정된 내용", PostCategory.CHAT);

            // when & then
            mockMvc.perform(put("/api/v1/posts/{postId}", savedPost.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.title").value("수정된 제목"))
                    .andExpect(jsonPath("$.data.content").value("수정된 내용"))
                    .andExpect(jsonPath("$.data.category").value("CHAT"));
        }

        @Test
        @DisplayName("실패 - 다른 사용자 게시글 수정")
        @Sql(statements = {
                "UPDATE users SET id = 1 WHERE login_id = 'testLoginId'",
                "UPDATE users SET id = 2 WHERE login_id = 'anotherLoginId'"
        })
        void fail_UnauthorizedUser() throws Exception {
            // given - ID=2인 사용자의 게시글 (ID=1 사용자가 수정 시도)
            User user2 = userRepository.findById(2L).orElseThrow();
            Post savedPost = postRepository.save(
                    Post.builder()
                            .title("다른 사용자 게시글")
                            .content("다른 사용자 내용")
                            .category(PostCategory.CHAT)
                            .user(user2)
                            .build()
            );

            PostRequest updateRequest = new PostRequest("수정 시도", "수정 시도 내용", PostCategory.CHAT);

            // when & then
            mockMvc.perform(put("/api/v1/posts/{postId}", savedPost.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED_USER.getCode()));
        }
    }
}