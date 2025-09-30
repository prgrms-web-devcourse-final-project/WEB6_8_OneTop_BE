package com.back.global.initdata;

import com.back.domain.comment.entity.Comment;
import com.back.domain.comment.repository.CommentRepository;
import com.back.domain.node.dto.PivotListDto;
import com.back.domain.node.dto.base.BaseLineBulkCreateRequest;
import com.back.domain.node.dto.base.BaseLineBulkCreateResponse;
import com.back.domain.node.dto.decision.DecNodeDto;
import com.back.domain.node.dto.decision.DecisionNodeFromBaseRequest;
import com.back.domain.node.dto.decision.DecisionNodeNextRequest;
import com.back.domain.node.entity.NodeCategory;
import com.back.domain.node.service.NodeService;
import com.back.domain.post.entity.Post;
import com.back.domain.post.enums.PostCategory;
import com.back.domain.post.repository.PostRepository;
import com.back.domain.user.entity.Gender;
import com.back.domain.user.entity.Mbti;
import com.back.domain.user.entity.Role;
import com.back.domain.user.entity.User;
import com.back.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * [요약] 기동 시 admin·user1 생성 → user1에 베이스라인(총7: 헤더+피벗5+테일) 1개와 결정라인(총5 노드) 1개 시드 주입.
 *        게시글 30개(일반20 + 투표10)과 댓글 14개(마지막 2개 글에 각 7개) 생성.
 */
@Component
@RequiredArgsConstructor
public class InitData implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NodeService nodeService;

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    // user1을 만들고 베이스라인(7)과 결정라인(5)을 시드로 주입한다
    @Override
    public void run(String... args) {
        if (userRepository.findByEmail("admin@example.com").isEmpty()) {
            var admin = User.builder()
                    .email("admin@example.com")
                    .password(passwordEncoder.encode("admin1234!"))
                    .role(Role.ADMIN)
                    .username("관리자")
                    .nickname("관리자닉네임")
                    .birthdayAt(LocalDateTime.of(1990, 1, 1, 0, 0))
                    .gender(Gender.M)
                    .mbti(Mbti.INTJ)
                    .beliefs("합리주의")
                    .build();
            userRepository.save(admin);
        }

        var user1 = userRepository.findByEmail("user1@example.com")
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .email("user1@example.com")
                                .password(passwordEncoder.encode("user1234!"))
                                .role(Role.USER)
                                .username("사용자1")
                                .nickname("사용자닉네임")
                                .birthdayAt(LocalDateTime.of(1995, 5, 10, 0, 0))
                                .gender(Gender.F)
                                .mbti(Mbti.ENFP)
                                .beliefs("개인주의")
                                .build()
                ));

        BaseLineBulkCreateResponse baseRes = nodeService.createBaseLineWithNodes(
                new BaseLineBulkCreateRequest(
                        user1.getId(),
                        "user1-기본 라인",
                        List.of(
                                new BaseLineBulkCreateRequest.BaseNodePayload(
                                        NodeCategory.EDUCATION, "중학교 진학", "일반계 선택", 18, "중등 입학 및 진로 탐색 시작"
                                ),
                                new BaseLineBulkCreateRequest.BaseNodePayload(
                                        NodeCategory.EDUCATION, "고교 진학", "이과 트랙", 20, "수학·물리 집중 선택"
                                ),
                                new BaseLineBulkCreateRequest.BaseNodePayload(
                                        NodeCategory.EDUCATION, "대학 합격", "컴공 전공", 22, "알고리즘/네트워크 관심"
                                ),
                                new BaseLineBulkCreateRequest.BaseNodePayload(
                                        NodeCategory.CAREER, "인턴 경험", "백엔드 인턴", 24, "스프링 부트 실무 체험"
                                ),
                                new BaseLineBulkCreateRequest.BaseNodePayload(
                                        NodeCategory.CAREER, "첫 직장", "주니어 백엔드", 26, "API/DB 설계 중심"
                                )
                        )
                )
        );

        Long baseLineId = baseRes.baseLineId();
        PivotListDto pivots = nodeService.getPivotBaseNodes(baseLineId);
        if (pivots.pivots() == null || pivots.pivots().isEmpty()) return;

        DecNodeDto d0 = nodeService.createDecisionNodeFromBase(
                new DecisionNodeFromBaseRequest(
                        user1.getId(),
                        baseLineId,
                        0,
                        null,
                        0,
                        NodeCategory.CAREER,
                        "개발자 커리어 진입",
                        List.of("자바/스프링", "파이썬/데이터"),
                        0,
                        "백엔드 중심 트랙을 초기 선택지로 제시"
                )
        );

        DecNodeDto d1 = nodeService.createDecisionNodeNext(
                new DecisionNodeNextRequest(
                        user1.getId(),
                        d0.id(),
                        NodeCategory.CAREER,
                        "클라우드 기초",
                        null,
                        List.of("AWS 기초", "GCP 기초"),
                        0,
                        0,
                        "EC2/RDS·CI/CD 파이프라인 구축"
                )
        );

        DecNodeDto d2 = nodeService.createDecisionNodeNext(
                new DecisionNodeNextRequest(
                        user1.getId(),
                        d1.id(),
                        NodeCategory.CAREER,
                        "보안 기초",
                        null,
                        List.of("웹 보안", "네트워크 보안"),
                        0,
                        0,
                        "JWT·세션·CSRF/XSS 대응 심화"
                )
        );

        DecNodeDto d3 = nodeService.createDecisionNodeNext(
                new DecisionNodeNextRequest(
                        user1.getId(),
                        d2.id(),
                        NodeCategory.CAREER,
                        "대용량 처리",
                        null,
                        List.of("캐시·큐", "검색"),
                        0,
                        0,
                        "Redis·Kafka·Elasticsearch 실습"
                )
        );

        nodeService.createDecisionNodeNext(
                new DecisionNodeNextRequest(
                        user1.getId(),
                        d3.id(),
                        NodeCategory.CAREER,
                        "운영/관측성",
                        null,
                        List.of("로그·모니터링", "SLO/알림"),
                        0,
                        0,
                        "프로덕션 운영 지표와 알림 체계 정착"
                )
        );

        if (postRepository.count() > 0) {
            return;
        }

        // 잠담 게시글 20개 생성
        List<Post> posts = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            Post post = Post.builder()
                    .title("일반 게시글 " + i)
                    .content("일반 게시글 내용 " + i)
                    .category(PostCategory.CHAT)
                    .user(user1)
                    .hide(false)
                    .likeCount(0)
                    .build();
            posts.add(post);
        }

        // 투표 게시글 10개 생성
        for (int i = 1; i <= 10; i++) {
            String voteContent = """
                    {
                      "pollUid": "%s",
                      "options": [
                        {"index": 1, "text": "첫 번째 옵션"},
                        {"index": 2, "text": "두 번째 옵션"},
                        {"index": 3, "text": "세 번째 옵션"}
                      ]
                    }
                    """.formatted(UUID.randomUUID().toString());

            Post pollPost = Post.builder()
                    .title("투표 게시글 " + i)
                    .content("투표 게시글 내용 " + i)
                    .category(PostCategory.POLL)
                    .user(user1)
                    .voteContent(voteContent)
                    .hide(false)
                    .likeCount(0)
                    .build();
            posts.add(pollPost);
        }

        postRepository.saveAll(posts);

        // 마지막 2개의 게시글만 댓글 7개 작성
        List<Post> lastPosts = posts.subList(posts.size() - 2, posts.size());

        List<Comment> comments = new ArrayList<>();

        for (Post post : lastPosts) {
            for (int i = 1; i <= 7; i++) {
                Comment comment = Comment.builder()
                        .post(post)
                        .user(user1) // 댓글 작성자
                        .content(post.getTitle() + "에 대한 댓글 " + i)
                        .hide(false)
                        .likeCount(0)
                        .build();
                comments.add(comment);
            }
        }
        commentRepository.saveAll(comments);
    }
}
