package com.back.domain.node.entity;

import com.back.domain.user.entity.User;
import com.back.global.baseentity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 사용자의 선택 분기(노드)를 나타내는 엔티티.
 * DecisionLine에 속하며, 계층 구조를 가질 수 있습니다.
 */
@Entity
@Table(name = "decision_nodes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DecisionNode extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NodeType nodeKind;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dec_line_id")
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

    private int decisionDate;
}