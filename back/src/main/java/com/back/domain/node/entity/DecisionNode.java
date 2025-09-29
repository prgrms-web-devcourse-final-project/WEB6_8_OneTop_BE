/**
 * [ENTITY] 사용자의 선택 분기(노드)
 * - 같은 DecisionLine 내에서 ageYear는 중복 불가(피벗 동기화)
 */
package com.back.domain.node.entity;

import com.back.domain.user.entity.User;
import com.back.global.baseentity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "decision_nodes",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_decision_line_age", columnNames = {"dec_line_id", "ageYear"})
        }
)
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
    private String option1; // 선택지1

    @Column(length = 255)
    private String option2; // 선택지2

    @Column(length = 255)
    private String option3; // 선택지3

    private Integer selectedIndex; // 0..2

    private Integer parentOptionIndex; // 부모 결정의 어떤 옵션(0..2)에서 파생됐는지

    @Column(columnDefinition = "TEXT")
    private String description; // 추가 설명

    // 다음 나이 검증
    public void guardNextAgeValid(int nextAge) {
        if (nextAge <= this.getAgeYear()) {
            throw new IllegalArgumentException("ageYear must be greater than parent's ageYear");
        }
    }
}
