/**
 * [ENTITY] 사용자의 현재 삶의 분기점(Base 노드)
 * - 베이스라인에 속하며 선형(parent 체인)으로 연결
 */
package com.back.domain.node.entity;

import com.back.domain.user.entity.User;
import com.back.global.baseentity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "base_nodes")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class BaseNode extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NodeType nodeKind;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_line_id")
    private BaseLine baseLine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_node_id")
    private BaseNode parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BaseNode> children = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private NodeCategory category;

    @Column(columnDefinition = "TEXT")
    private String situation;

    @Column(columnDefinition = "TEXT")
    private String decision;

    private int ageYear;

    @Column(length = 255)
    private String fixedChoice; // 고정 선택 1개

    @Column(length = 255)
    private String altOpt1; // 분기 선택지 1

    @Column(length = 255)
    private String altOpt2; // 분기 선택지 2

    private Long altOpt1TargetDecisionId; // 분기1 연결 대상 결정노드 id

    private Long altOpt2TargetDecisionId; // 분기2 연결 대상 결정노드 id

    @Column(columnDefinition = "TEXT")
    private String description; // 추가 설명

    // 헤더 판단 헬퍼
    public boolean isHeaderOf(BaseLine baseLine) {
        if (baseLine == null) return false;
        return this.getBaseLine() != null
                && Objects.equals(this.getBaseLine().getId(), baseLine.getId())
                && this.getParent() == null;
    }

    // 베이스 분기 규칙 검증
    public void guardBaseOptionsValid() {
        if (fixedChoice == null || fixedChoice.isBlank()) throw new IllegalArgumentException("fixedChoice required");
        if (altOpt1 != null && altOpt1.isBlank()) throw new IllegalArgumentException("altOpt1 blank");
        if (altOpt2 != null && altOpt2.isBlank()) throw new IllegalArgumentException("altOpt2 blank");
    }
}
