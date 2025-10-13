/**
 * [ENTITY] BaseNode (추가 필드 포함)
 * - NodeAtomVersion 참조를 통해 현재 적용 중인 콘텐츠 버전을 가리킴
 * - 기존 필드는 변경하지 않으며, 마이그레이션 후 currentVersion을 채워 읽기 해석에 사용
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
    private String fixedChoice;

    @Column(length = 255)
    private String altOpt1;

    @Column(length = 255)
    private String altOpt2;

    private Long altOpt1TargetDecisionId;

    private Long altOpt2TargetDecisionId;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_version_id")
    private NodeAtomVersion currentVersion;

    // 헤더 노드 판별
    public boolean isHeaderOf(BaseLine baseLine) {
        if (baseLine == null) return false;
        return this.getBaseLine() != null
                && Objects.equals(this.getBaseLine().getId(), baseLine.getId())
                && this.getParent() == null;
    }

    // 분기 슬롯 유효성 검증
    public void guardBaseOptionsValid() {
        if (fixedChoice == null || fixedChoice.isBlank()) throw new IllegalArgumentException("fixedChoice required");
        if (altOpt1 != null && altOpt1.isBlank()) throw new IllegalArgumentException("altOpt1 blank");
        if (altOpt2 != null && altOpt2.isBlank()) throw new IllegalArgumentException("altOpt2 blank");
    }
}
