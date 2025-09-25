package com.back.domain.comment.controller;

import com.back.domain.comment.dto.CommentRequest;
import com.back.domain.comment.repository.CommentRepository;
import com.back.domain.post.entity.Post;
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

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    private Post testPost;

    @BeforeEach
    void setUp() {
        String uid = UUID.randomUUID().toString().substring(0, 5);

        User user = User.builder()
                .loginId("loginId_" + uid)
                .email("test" + uid + "@example.com")
                .password("password")
                .nickname("tester_" + uid)
                .beliefs("도전")
                .gender(Gender.M)
                .role(Role.USER)
                .mbti(Mbti.ISFJ)
                .birthdayAt(LocalDateTime.of(2000, 1, 1, 0, 0))
                .build();
        testUser = userRepository.save(user);

        Post post = Post.builder()
                .title("테스트 게시글")
                .content("테스트 내용")
                .user(user)
                .build();
        testPost = postRepository.save(post);
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
}