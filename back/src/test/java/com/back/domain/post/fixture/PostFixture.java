package com.back.domain.post.fixture;


import com.back.domain.post.dto.PostRequest;
import com.back.domain.post.entity.Post;
import com.back.domain.post.enums.PostCategory;
import com.back.domain.post.repository.PostRepository;
import com.back.domain.user.entity.Gender;
import com.back.domain.user.entity.Mbti;
import com.back.domain.user.entity.Role;
import com.back.domain.user.entity.User;
import com.back.domain.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 테스트에 필요한 데이터 관리 클래스
 */
public class PostFixture {

    public static final String API_BASE_PATH = "/api/v1/posts";
    public static final Long NON_EXISTENT_POST_ID = 9999L;

    private final UserRepository userRepository;
    private final PostRepository postRepository;

    public PostFixture(UserRepository userRepository, PostRepository postRepository) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
    }

    // User 생성
    public User createTestUser() {
        return createUser("testLoginId", "test@example.com", "testPassword", "작성자1", "닉네임1", Gender.M);
    }

    public User createAnotherUser() {
        return createUser("anotherLoginId", "another@example.com", "another", "작성자2", "닉네임2", Gender.F);
    }

    private User createUser(String loginId, String email, String password, String username, String nickname, Gender gender) {
        return userRepository.save(User.builder()
                .email(email)
                .password(password)
                .username(username)
                .nickname(nickname)
                .beliefs("도전")
                .gender(gender)
                .role(Role.USER)
                .mbti(Mbti.ISFJ)
                .birthdayAt(LocalDateTime.of(2000, 1, 1, 0, 0))
                .build());
    }

    // Post 생성
    public Post createPost(User user, String title, String content, PostCategory category) {
        return postRepository.save(Post.builder()
                .title(title)
                .content(content)
                .category(category)
                .user(user)
                .build());
    }

    public List<Post> createPostsForPaging(User user, int count) {
        List<Post> posts = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            posts.add(createPost(user, "목록 게시글 " + i, "목록 내용 " + i, PostCategory.CHAT));
        }
        for (int i = 1; i <= count / 2; i++) {
            posts.add(createPost(user, "시나리오 게시글 " + i, "시나리오 내용 " + i, PostCategory.SCENARIO));
        }
        return posts;
    }

    public Post createPostForDetail(User user) {
        return createPost(user, "조회 테스트 게시글", "조회 테스트 내용입니다.", PostCategory.CHAT);
    }

    public Post createPostForUpdate(User user) {
        return createPost(user, "수정 전 제목", "수정 전 내용", PostCategory.CHAT);
    }

    // PostRequest 생성
    public PostRequest createPostRequest(String title, String content, PostCategory category) {
        return new PostRequest(title, content, category, false);
    }

    public PostRequest createPostRequest() {
        return createPostRequest("테스트 게시글", "테스트 내용입니다.", PostCategory.CHAT);
    }

    public PostRequest createEmptyTitleRequest() {
        return createPostRequest("", "테스트 내용입니다.", PostCategory.CHAT);
    }

    public PostRequest createEmptyContentRequest() {
        return createPostRequest("테스트 게시글", "", PostCategory.CHAT);
    }

    public PostRequest createUpdateRequest() {
        return createPostRequest("수정된 제목", "수정된 내용", PostCategory.CHAT);
    }
}

