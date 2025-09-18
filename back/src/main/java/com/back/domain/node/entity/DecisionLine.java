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
 * 사용자의 선택 분기(Decision Node)들의 연결을 나타내는 엔티티.
 * 각 사용자는 여러 개의 DecisionLine을 가질 수 있습니다.
 */
@Entity
@Table(name = "decision_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DecisionLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "decisionLine", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DecisionNode> decisionNodes = new ArrayList<>();
}