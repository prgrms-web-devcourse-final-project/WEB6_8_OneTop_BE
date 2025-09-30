package com.back.global.initdata;

import com.back.domain.node.dto.PivotListDto;
import com.back.domain.node.dto.base.BaseLineBulkCreateRequest;
import com.back.domain.node.dto.base.BaseLineBulkCreateResponse;
import com.back.domain.node.dto.decision.DecNodeDto;
import com.back.domain.node.dto.decision.DecisionNodeFromBaseRequest;
import com.back.domain.node.dto.decision.DecisionNodeNextRequest;
import com.back.domain.node.entity.NodeCategory;
import com.back.domain.node.service.NodeService;
import com.back.domain.user.entity.*;
import com.back.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * [요약] 기동 시 admin·user1 생성 → user1에 베이스라인(총7: 헤더+피벗5+테일) 1개와 결정라인(총5 노드) 1개 시드 주입.
 */
@Component
@RequiredArgsConstructor
public class InitData implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NodeService nodeService;

    // 가장 중요한 함수 위에 한줄로만 요약
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

        // 피벗 5개만 전송(서비스가 헤더/테일 자동 부착 -> 총 7노드)
        BaseLineBulkCreateResponse baseRes = nodeService.createBaseLineWithNodes(
                new BaseLineBulkCreateRequest(
                        user1.getId(),
                        "user1-기본 라인",
                        List.of(
                                new BaseLineBulkCreateRequest.BaseNodePayload(
                                        NodeCategory.EDUCATION, "중학교 진학", "일반계 선택", 13, "중등 입학 및 진로 탐색 시작"
                                ),
                                new BaseLineBulkCreateRequest.BaseNodePayload(
                                        NodeCategory.EDUCATION, "고교 진학", "이과 트랙", 16, "수학·물리 집중 선택"
                                ),
                                new BaseLineBulkCreateRequest.BaseNodePayload(
                                        NodeCategory.EDUCATION, "대학 합격", "컴공 전공", 19, "알고리즘/네트워크 관심"
                                ),
                                new BaseLineBulkCreateRequest.BaseNodePayload(
                                        NodeCategory.CAREER, "인턴 경험", "백엔드 인턴", 23, "스프링 부트 실무 체험"
                                ),
                                new BaseLineBulkCreateRequest.BaseNodePayload(
                                        NodeCategory.CAREER, "첫 직장", "주니어 백엔드", 25, "API/DB 설계 중심"
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
                        List.of("자바/스프링", "파이썬/데이터", "프론트/리액트"),
                        null,
                        "백엔드 중심 트랙을 초기 선택지로 제시"
                )
        );

        nodeService.createDecisionNodeNext(
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
                        d0.id(),
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
                        null,
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
                        null,
                        "프로덕션 운영 지표와 알림 체계 정착"
                )
        );
    }
}
