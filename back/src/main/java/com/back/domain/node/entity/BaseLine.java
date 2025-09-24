package com.back.domain.node.entity;

import com.back.domain.user.entity.User;
import com.back.global.baseentity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 사용자의 현재 삶의 베이스라인 엔티티
 * - user: 소유 사용자
 * - baseNodes: 이 라인에 속한 BaseNode 목록(양방향)
 */
@Entity
@Table(name = "base_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class BaseLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 100, nullable = false)
    private String title;

    // BaseLine ←→ BaseNode 양방향 매핑 (BaseNode 쪽에 @ManyToOne BaseLine baseLine 있어야 함)
    @OneToMany(mappedBy = "baseLine", cascade = CascadeType.ALL, orphanRemoval = false)
    @Builder.Default
    private List<BaseNode> baseNodes = new ArrayList<>();

    /**
     * 중간 피벗 나이 목록 반환(헤더/꼬리 제외, 중복 제거, 오름차순)
     */
    public List<Integer> pivotAges() {
        List<BaseNode> nodes = this.baseNodes;
        if (nodes == null || nodes.size() <= 2) {
            return List.of();
        }

        // 정렬 사본 생성 (ageYear 오름차순, 동일 시 id 오름차순; null은 맨 뒤)
        List<BaseNode> sorted = new ArrayList<>(nodes);
        sorted.sort(
                Comparator
                        .comparing(BaseNode::getAgeYear,
                                Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(BaseNode::getId,
                                Comparator.nullsLast(Long::compareTo))
        );

        // 헤더(0)와 꼬리(마지막) 제외 + distinct
        List<Integer> ages = new ArrayList<>();
        for (int i = 1; i < sorted.size() - 1; i++) {
            Integer age = sorted.get(i).getAgeYear();
            if (age == null) continue;
            if (ages.isEmpty() || !ages.get(ages.size() - 1).equals(age)) {
                ages.add(age);
            }
        }
        return ages;
    }
}
