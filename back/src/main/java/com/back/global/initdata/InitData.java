package com.back.global.initdata;

import com.back.domain.comment.entity.Comment;
import com.back.domain.comment.repository.CommentRepository;
import com.back.domain.node.dto.PivotListDto;
import com.back.domain.node.dto.base.BaseLineBulkCreateRequest;
import com.back.domain.node.dto.base.BaseLineBulkCreateResponse;
import com.back.domain.node.dto.decision.DecNodeDto;
import com.back.domain.node.dto.decision.DecisionNodeFromBaseRequest;
import com.back.domain.node.dto.decision.DecisionNodeNextRequest;
import com.back.domain.node.entity.BaseLine;
import com.back.domain.node.entity.DecisionLine;
import com.back.domain.node.entity.NodeCategory;
import com.back.domain.node.repository.BaseLineRepository;
import com.back.domain.node.repository.DecisionLineRepository;
import com.back.domain.node.service.NodeService;
import com.back.domain.post.entity.Post;
import com.back.domain.post.enums.PostCategory;
import com.back.domain.post.repository.PostRepository;
import com.back.domain.scenario.entity.SceneType;
import com.back.domain.scenario.entity.Scenario;
import com.back.domain.scenario.entity.ScenarioStatus;
import com.back.domain.scenario.entity.Type;
import com.back.domain.scenario.repository.SceneTypeRepository;
import com.back.domain.scenario.repository.ScenarioRepository;
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
 * [요약] 기동 시 admin·user1 생성 → user1에 베이스라인(총7: 헤더+피벗5+테일) 1개와 결정라인 2개(첫 번째 6노드, 두 번째 2노드) 시드 주입.
 *        게시글 30개(일반20 + 투표10)과 댓글 14개(마지막 2개 글에 각 7개) 생성.
 *        시나리오 3개(베이스 1개 + 완성 1개 + 처리중 1개)와 지표 10개(완성된 시나리오 2개에 각 5개씩) 생성.
 */
@Component
@RequiredArgsConstructor
public class InitData implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NodeService nodeService;

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    private final BaseLineRepository baseLineRepository;
    private final DecisionLineRepository decisionLineRepository;
    private final ScenarioRepository scenarioRepository;
    private final SceneTypeRepository sceneTypeRepository;

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

        DecNodeDto d4 = nodeService.createDecisionNodeNext(
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

        // 두 번째 DecisionLine 생성 (processingScenario 테스트용)
        DecNodeDto d5 = nodeService.createDecisionNodeFromBase(
                new DecisionNodeFromBaseRequest(
                        user1.getId(),
                        baseLineId,
                        1,  // 두 번째 피벗
                        null,
                        0,
                        NodeCategory.CAREER,
                        "스타트업 창업",
                        List.of("단독 창업", "공동 창업"),
                        0,
                        "기술 스타트업 설립 선택지"
                )
        );

        DecNodeDto d6 = nodeService.createDecisionNodeNext(
                new DecisionNodeNextRequest(
                        user1.getId(),
                        d5.id(),
                        NodeCategory.FINANCE,
                        "초기 투자 유치",
                        null,
                        List.of("엔젤 투자", "시드 투자"),
                        0,
                        0,
                        "초기 자금 확보 전략"
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

        // ========== Scenario InitData 생성 ==========

        // 시나리오 데이터가 이미 있으면 스킵
        if (scenarioRepository.count() > 0) {
            return;
        }

        // BaseLine 조회 (위에서 생성한 baseLineId 사용)
        BaseLine baseLine = baseLineRepository.findById(baseLineId)
                .orElseThrow(() -> new IllegalStateException("BaseLine not found"));

        // DecisionLine 조회 (user1의 DecisionLine - BaseLine에 연결된 것)
        List<DecisionLine> decisionLines = decisionLineRepository.findByBaseLine_Id(baseLineId);
        if (decisionLines.size() < 2) {
            throw new IllegalStateException("Expected 2 DecisionLines but found: " + decisionLines.size());
        }

        DecisionLine decisionLine1 = decisionLines.get(0);  // 첫 번째 DecisionLine (완성 시나리오용)
        DecisionLine decisionLine2 = decisionLines.get(1);  // 두 번째 DecisionLine (처리중 시나리오용)

        // 1. 베이스 시나리오 생성 (현재 삶 기준)
        Scenario baseScenario = Scenario.builder()
                .user(user1)
                .baseLine(baseLine)
                .decisionLine(null)  // 베이스 시나리오는 DecisionLine 없음
                .status(ScenarioStatus.COMPLETED)
                .job("주니어 백엔드 개발자")
                .total(350)
                .summary("현재 커리어 경로를 유지하며 안정적으로 성장하는 시나리오입니다.")
                .description("""
                        대학 졸업 후 백엔드 개발자로 시작하여 주니어에서 시니어로 성장하는 과정입니다.
                        안정적인 중견기업에서 경력을 쌓으며, 스프링 부트와 API 설계 전문성을 키워나갑니다.
                        팀 내에서 신뢰받는 개발자로 성장하며, 워라밸을 유지하면서 건강한 개발 생활을 이어갑니다.
                        """)
                .timelineTitles("""
                        [
                          {"year":2025,"title":"시니어 개발자 승진"},
                          {"year":2027,"title":"테크 리드 역할 수행"},
                          {"year":2030,"title":"개발팀 리더"}
                        ]
                        """)
                .img("https://picsum.photos/seed/base-scenario/400/300")
                .build();
        scenarioRepository.save(baseScenario);

        // 베이스 시나리오 지표 생성 (5개)
        createSceneTypes(baseScenario, 70, 75, 70, 65, 70);

        // 2. 완성된 시나리오 생성 (첫 번째 DecisionLine 기반)
        Scenario completedScenario = Scenario.builder()
                .user(user1)
                .baseLine(baseLine)  // 베이스 시나리오와 같은 BaseLine 참조
                .decisionLine(decisionLine1)
                .status(ScenarioStatus.COMPLETED)
                .job("클라우드 아키텍트")
                .total(430)
                .summary("클라우드와 보안 전문성을 갖춘 시니어 아키텍트로 성장하는 시나리오입니다.")
                .description("""
                        AWS/GCP 클라우드 플랫폼 심화 학습과 보안 인증을 통해 시스템 아키텍트로 성장합니다.
                        대용량 트래픽 처리 경험과 인프라 자동화 능력을 갖추어 기술 리더로 인정받습니다.
                        컨설팅 프로젝트와 기술 강연을 통해 업계 전문가로 자리매김하며,
                        궁극적으로 스타트업 CTO 또는 대기업 기술 이사로 성장합니다.
                        """)
                .timelineTitles("""
                        [
                          {"year":2025,"title":"AWS Solutions Architect 자격증 취득"},
                          {"year":2026,"title":"보안 전문가 인증 (CISSP)"},
                          {"year":2028,"title":"솔루션 아키텍트 승진"},
                          {"year":2031,"title":"기술 이사 (CTO)"}
                        ]
                        """)
                .img("https://picsum.photos/seed/decision-scenario/400/300")
                .build();
        scenarioRepository.save(completedScenario);

        // 완성 시나리오 지표 생성 (더 높은 점수)
        createSceneTypes(completedScenario, 90, 85, 80, 88, 87);

        // 3. 처리 중 시나리오 생성 (두 번째 DecisionLine 기반, 폴링 테스트용)
        Scenario processingScenario = Scenario.builder()
                .user(user1)
                .baseLine(baseLine)
                .decisionLine(decisionLine2)  // 두 번째 DecisionLine 사용
                .status(ScenarioStatus.PROCESSING)
                .build();
        scenarioRepository.save(processingScenario);
    }

    /**
     * 시나리오에 대한 5개 지표(SceneType) 데이터를 생성합니다.
     * @param scenario 대상 시나리오
     * @param eco 경제 점수
     * @param happy 행복 점수
     * @param rel 관계 점수
     * @param career 직업 점수
     * @param health 건강 점수
     */
    private void createSceneTypes(Scenario scenario, int eco, int happy, int rel, int career, int health) {
        List<SceneType> sceneTypes = List.of(
                SceneType.builder()
                        .scenario(scenario)
                        .type(Type.경제)
                        .point(eco)
                        .analysis(eco >= 85
                                ? "클라우드 전문가로 높은 연봉과 컨설팅 수입을 통해 경제적 자유를 확보했습니다."
                                : "안정적인 중견기업 재직으로 평균 이상의 경제력을 유지하고 있습니다.")
                        .build(),
                SceneType.builder()
                        .scenario(scenario)
                        .type(Type.행복)
                        .point(happy)
                        .analysis(happy >= 80
                                ? "전문성 인정과 도전적인 업무를 통해 높은 직무 만족도를 느낍니다."
                                : "업무 만족도가 높고 워라밸이 좋은 환경에서 일하고 있습니다.")
                        .build(),
                SceneType.builder()
                        .scenario(scenario)
                        .type(Type.관계)
                        .point(rel)
                        .analysis(rel >= 80
                                ? "리더십 역할을 통해 업계 네트워크를 넓히고 멘토 관계를 형성했습니다."
                                : "팀원들과 원만한 관계를 유지하며 개인 시간도 충분히 확보하고 있습니다.")
                        .build(),
                SceneType.builder()
                        .scenario(scenario)
                        .type(Type.직업)
                        .point(career)
                        .analysis(career >= 85
                                ? "클라우드 및 보안 분야 최고 전문가로 인정받고 있습니다."
                                : "백엔드 개발 전문성은 확보했으나 리더십 경험이 다소 부족합니다.")
                        .build(),
                SceneType.builder()
                        .scenario(scenario)
                        .type(Type.건강)
                        .point(health)
                        .analysis(health >= 85
                                ? "체계적인 건강 관리와 규칙적인 운동 루틴을 유지하고 있습니다."
                                : "규칙적인 생활과 적당한 운동으로 건강을 유지하고 있습니다.")
                        .build()
        );

        sceneTypeRepository.saveAll(sceneTypes);
    }
}
