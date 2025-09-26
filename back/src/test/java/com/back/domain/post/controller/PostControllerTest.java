package com.back.domain.post.controller;

import com.back.domain.post.dto.PostRequest;
import com.back.domain.post.entity.Post;
import com.back.domain.post.enums.PostCategory;
import com.back.domain.post.enums.SearchType;
import com.back.domain.post.fixture.PostFixture;
import com.back.domain.post.repository.PostRepository;
import com.back.domain.user.entity.User;
import com.back.domain.user.repository.UserRepository;
import com.back.global.exception.ErrorCode;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
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

    private PostFixture fixture;
    private User testUser;
    private User anotherUser;

    @BeforeEach
    void setUp() {
        fixture = new PostFixture(userRepository, postRepository);
        testUser = fixture.createTestUser();
        anotherUser = fixture.createAnotherUser();
        fixture.createPostsForPaging(testUser, 5);
    }

    @Nested
    @DisplayName("게시글 생성")
    class CreatePost {

        @Test
        @DisplayName("성공 - 정상 요청")
        void success() throws Exception {
            // given
            PostRequest request = fixture.createPostRequest();

            // when & then
            mockMvc.perform(post(PostFixture.API_BASE_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .param("userId", String.valueOf(testUser.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.title").value("테스트 게시글"))
                    .andExpect(jsonPath("$.data.content").value("테스트 내용입니다."))
                    .andExpect(jsonPath("$.data.category").value("CHAT"))
                    .andExpect(jsonPath("$.message").value("성공적으로 생성되었습니다."))
                    .andExpect(jsonPath("$.status").value(200));
        }

        @Test
        @DisplayName("실패 - 유효성 검사 실패 (빈 제목)")
        void fail_ValidationError_EmptyTitle() throws Exception {
            // given
            PostRequest request = fixture.createEmptyTitleRequest();

            // when & then
            mockMvc.perform(post(PostFixture.API_BASE_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .param("userId", String.valueOf(testUser.getId())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()))
                    .andExpect(jsonPath("$.message").exists());
        }

        @Test
        @DisplayName("실패 - 유효성 검사 실패 (빈 내용)")
        void fail_ValidationError_EmptyContent() throws Exception {
            // given
            PostRequest request = fixture.createEmptyContentRequest();

            // when & then
            mockMvc.perform(post(PostFixture.API_BASE_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .param("userId", String.valueOf(testUser.getId())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));
        }
    }

    @Nested
    @DisplayName("게시글 조회")
    class GetPost {

        @Test
        @DisplayName("성공 - 존재하는 게시글 조회")
        void success() throws Exception {
            // given
            Post savedPost = fixture.createPostForDetail(testUser);

            // when & then
            mockMvc.perform(get(PostFixture.API_BASE_PATH + "/{postId}", savedPost.getId())
                            .param("userId", String.valueOf(testUser.getId())))
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
            mockMvc.perform(get(PostFixture.API_BASE_PATH + "/{postId}", PostFixture.NON_EXISTENT_POST_ID)
                            .param("userId", String.valueOf(testUser.getId())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(HttpStatus.NOT_FOUND.value()))
                    .andExpect(jsonPath("$.code").value(ErrorCode.POST_NOT_FOUND.getCode()))
                    .andExpect(jsonPath("$.message").value(ErrorCode.POST_NOT_FOUND.getMessage()))
                    .andExpect(jsonPath("$.path").value(PostFixture.API_BASE_PATH + "/" + PostFixture.NON_EXISTENT_POST_ID));
        }
    }

    @Nested
    @DisplayName("게시글 목록 조회")
    class GetPosts {

        @Test
        @DisplayName("성공 - 페이징 파라미터가 없는 경우")
        void successWithDefaultParameters() throws Exception {
            // when & then
            mockMvc.perform(get(PostFixture.API_BASE_PATH)
                            .param("userId", String.valueOf(testUser.getId())))
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
            mockMvc.perform(get(PostFixture.API_BASE_PATH)
                            .param("page", "1")
                            .param("size", "5")
                            .param("userId", String.valueOf(testUser.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items.length()").value(5))
                    .andExpect(jsonPath("$.data.items[0].title").value("시나리오 게시글 2"))
                    .andExpect(jsonPath("$.data.items[1].title").value("시나리오 게시글 1"))
                    .andExpect(jsonPath("$.data.items[2].title").value("목록 게시글 5"))
                    .andExpect(jsonPath("$.data.page").value(1))
                    .andExpect(jsonPath("$.data.size").value(5))
                    .andExpect(jsonPath("$.data.last").value(false));
        }

        @Test
        @DisplayName("성공 - 카테고리 필터링 적용")
        void successWithCategoryFilter() throws Exception {
            // when & then
            mockMvc.perform(get(PostFixture.API_BASE_PATH)
                            .param("category", PostCategory.SCENARIO.name())
                            .param("userId", String.valueOf(testUser.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[*].category").value(
                            Matchers.everyItem(Matchers.equalTo("SCENARIO"))
                    ))
                    .andExpect(jsonPath("$.data.items.length()").value(2))
                    .andDo(print());
        }

        @Test
        @DisplayName("성공 - 제목 + 내용 검색")
        void successWithTitleContentSearch() throws Exception {
            // when & then
            mockMvc.perform(get(PostFixture.API_BASE_PATH)
                            .param("searchType", SearchType.TITLE_CONTENT.name())
                            .param("keyword", "시나리오")
                            .param("userId", String.valueOf(testUser.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items.length()").value(2))
                    .andDo(print());
        }

        @Test
        @DisplayName("성공 - 작성자 검색")
        void successWithAuthorSearch() throws Exception {
            // when & then
            mockMvc.perform(get(PostFixture.API_BASE_PATH)
                            .param("searchType", SearchType.AUTHOR.name())
                            .param("keyword", "작성자1")
                            .param("userId", String.valueOf(testUser.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[*].author",
                            Matchers.everyItem(Matchers.containsStringIgnoringCase("작성자1"))))
                    .andDo(print());
        }
    }

    @Nested
    @DisplayName("게시글 수정")
    class UpdatePost {

        @Test
        @DisplayName("성공 - 본인 게시글 수정")
        void success() throws Exception {
            // given
            User user1 = userRepository.findById(testUser.getId()).orElseThrow();
            Post savedPost = fixture.createPostForUpdate(user1);
            PostRequest updateRequest = fixture.createUpdateRequest();

            // when & then
            mockMvc.perform(put(PostFixture.API_BASE_PATH + "/{postId}", savedPost.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest))
                            .param("userId", String.valueOf(testUser.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(savedPost.getId()))
                    .andDo(print());
        }

        @Test
        @DisplayName("실패 - 다른 사용자 게시글 수정")
        void fail_UnauthorizedUser() throws Exception {
            // given
            Post savedPost = fixture.createPostForUpdate(anotherUser);
            PostRequest updateRequest = fixture.createUpdateRequest();

            // when & then
            mockMvc.perform(put(PostFixture.API_BASE_PATH + "/{postId}", savedPost.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest))
                            .param("userId", String.valueOf(testUser.getId())))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED_USER.getCode()));
        }
    }
}