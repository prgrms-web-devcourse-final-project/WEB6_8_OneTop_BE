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
import com.back.global.security.CustomUserDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

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

        userRepository.save(anotherUser);

        CustomUserDetails cs = new CustomUserDetails(testUser);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(cs, null, cs.getAuthorities())
        );
    }

    @Nested
    @DisplayName("게시글 생성")
    class CreatePost {

        @Test
        @DisplayName("성공 - 정상 요청")
        void success() throws Exception {
            PostRequest request = new PostRequest("테스트 게시글", "테스트 내용입니다.", PostCategory.CHAT, false);

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
        @DisplayName("실패 - 유효성 검사 실패 (빈 제목)")
        void failEmptyTitle() throws Exception {
            PostRequest request = new PostRequest("", "내용", PostCategory.SCENARIO, false);

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
        private Post savedPost;

        @BeforeEach
        void createPost() {
            savedPost = Post.builder()
                    .title("조회 테스트 게시글")
                    .content("조회 테스트 내용입니다.")
                    .category(PostCategory.CHAT)
                    .user(testUser)
                    .build();
            postRepository.save(savedPost);
        }

        @Test
        @DisplayName("성공 - 존재하는 게시글 조회")
        void success() throws Exception {
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
        void failNotFound() throws Exception {
            mockMvc.perform(get("/api/v1/posts/{postId}", 999L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                    .andExpect(jsonPath("$.code").value(ErrorCode.POST_NOT_FOUND.getCode()))
                    .andExpect(jsonPath("$.message").value(ErrorCode.POST_NOT_FOUND.getMessage()))
                    .andExpect(jsonPath("$.path").value("/api/v1/posts/999"));
        }
    }

    @Nested
    @DisplayName("게시글 목록 조회")
    class GetPosts {
        @BeforeEach
        void createPosts() {
            for (int i = 1; i <= 5; i++) {
                Post post = Post.builder()
                        .title("게시글 " + i)
                        .content("내용 " + i)
                        .category(i % 2 == 0 ? PostCategory.SCENARIO : PostCategory.CHAT)
                        .user(testUser)
                        .build();
                postRepository.save(post);
            }
        }

        @Test
        @DisplayName("성공 - 기본 페이징")
        void successWithDefaultParameters() throws Exception {
            mockMvc.perform(get("/api/v1/posts"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.page").value(1))
                    .andExpect(jsonPath("$.data.size").value(5))
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.message").value("성공적으로 조회되었습니다."));
        }

        @Test
        @DisplayName("성공 - 카테고리 필터링")
        void successWithCategoryFilter() throws Exception {
            mockMvc.perform(get("/api/v1/posts")
                            .param("category", PostCategory.SCENARIO.name()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[*].category",
                            Matchers.everyItem(Matchers.equalTo("SCENARIO"))))
                    .andExpect(jsonPath("$.data.items.length()").value(2));
        }

        @Test
        @DisplayName("성공 - 제목 + 내용 검색")
        void successWithTitleContentSearch() throws Exception {
            mockMvc.perform(get("/api/v1/posts")
                            .param("searchType", "TITLE_CONTENT")
                            .param("keyword", "게시글"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items.length()").value(5));
        }

        @Test
        @DisplayName("성공 - 작성자 검색")
        void successWithAuthorSearch() throws Exception {
            mockMvc.perform(get("/api/v1/posts")
                            .param("searchType", "AUTHOR")
                            .param("keyword", "테스트유저"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[*].author",
                            Matchers.everyItem(Matchers.containsStringIgnoringCase("테스트유저"))));
        }
    }

    @Nested
    @DisplayName("게시글 수정")
    class UpdatePost {
        private Post savedPost;
        @BeforeEach
        void createPost() {
            savedPost = Post.builder()
                    .title("수정 테스트 게시글")
                    .content("수정 전 내용")
                    .category(PostCategory.CHAT)
                    .user(testUser)
                    .build();
            postRepository.save(savedPost);
        }

        @Test
        @DisplayName("성공 - 본인 게시글 수정")
        void success() throws Exception {
            PostRequest updateRequest = new PostRequest("수정된 제목", "수정된 내용", PostCategory.CHAT, false);

            mockMvc.perform(put("/api/v1/posts/{postId}", savedPost.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(savedPost.getId()));
        }

        @Test
        @DisplayName("실패 - 다른 사용자 게시글 수정")
        void failUnauthorizedUser() throws Exception {
            Post otherPost = Post.builder()
                    .title("다른 사용자 게시글")
                    .content("내용")
                    .category(PostCategory.CHAT)
                    .user(anotherUser)
                    .build();
            postRepository.save(otherPost);

            PostRequest updateRequest = new PostRequest("수정 시도", "수정 시도 내용", PostCategory.CHAT, false);

            mockMvc.perform(put("/api/v1/posts/{postId}", otherPost.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED_USER.getCode()));
        }
    }
}