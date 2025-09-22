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

    // com.back.domain.node.entity.BaseNode 안에 추가

    public boolean isHeaderOf(BaseLine baseLine) {
        if (baseLine == null) return false;
        // header 판단 기준이 따로 있다면 맞게 수정 (여기선 parent == null 가정)
        return this.getBaseLine() != null
                && Objects.equals(this.getBaseLine().getId(), baseLine.getId())
                && this.getParent() == null;
    }

}
