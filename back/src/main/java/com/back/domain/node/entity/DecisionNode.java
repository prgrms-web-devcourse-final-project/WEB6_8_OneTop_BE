/**
 * [ENTITY] DecisionNode (추가 필드 포함)
 * - 노드별 팔로우 정책과 오버라이드 버전을 보관하여 해석 시 최종 버전을 결정
 * - OVERRIDE면 overrideVersion을, PINNED/FOLLOW면 라인 기준 커밋/브랜치로 해석
 */
package com.back.domain.node.entity;

import com.back.domain.user.entity.User;
import com.back.global.baseentity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "decision_nodes")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class DecisionNode extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NodeType nodeKind;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dec_line_id", nullable = false)
    private DecisionLine decisionLine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_node_id")
    private BaseNode baseNode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_node_id")
    private DecisionNode parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DecisionNode> children = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private NodeCategory category;

    @Column(columnDefinition = "TEXT")
    private String situation;

    @Column(columnDefinition = "TEXT")
    private String decision;

    private int ageYear;

    @Column(columnDefinition = "TEXT")
    private String background;

    @Column(length = 255)
    private String option1;

    @Column(length = 255)
    private String option2;

    @Column(length = 255)
    private String option3;

    private Integer selectedIndex;

    private Integer parentOptionIndex;

    @Column(columnDefinition = "TEXT")
    private String description;

    // ▼ 추가: 해석 정책/버전
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private FollowPolicy followPolicy = FollowPolicy.FOLLOW;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "override_version_id")
    private NodeAtomVersion overrideVersion;

    @Column(name = "ai_next_situation", columnDefinition = "TEXT")
    private String aiNextSituation;

    @Column(name = "ai_next_recommended_option", columnDefinition = "TEXT")
    private String aiNextRecommendedOption;

    // 다음 나이 검증
    public void guardNextAgeValid(int nextAge) {
        if (nextAge <= this.getAgeYear()) {
            throw new IllegalArgumentException("ageYear must be greater than parent's ageYear");
        }
    }

    // OVERRIDE로 전환하고 지정 버전을 설정
    public void setOverride(NodeAtomVersion version) {
        this.followPolicy = FollowPolicy.OVERRIDE;
        this.overrideVersion = version;
    }

    public void setAiHint(String nextSituation, String nextRecommended) {
        this.aiNextSituation = nextSituation;
        this.aiNextRecommendedOption = nextRecommended;
    }
}
