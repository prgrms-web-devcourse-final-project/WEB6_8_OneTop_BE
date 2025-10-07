/**
 * [ENTITY] DecisionLine (추가 필드 포함)
 * - 라인이 따르는 베이스 브랜치와 고정 커밋을 보관하며, FOLLOW/PINNED 해석의 기준이 됨
 */
package com.back.domain.node.entity;

import com.back.domain.user.entity.User;
import com.back.global.baseentity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "decision_lines",
        indexes = {
                @Index(name = "idx_dline_branch", columnList = "base_branch_id"),
                @Index(name = "idx_dline_pinned", columnList = "pinned_commit_id")
        })
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class DecisionLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_line_id", nullable = false)
    private BaseLine baseLine;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DecisionLineStatus status;

    @OneToMany(mappedBy = "decisionLine", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DecisionNode> decisionNodes = new ArrayList<>();

    // ▼ 추가: 해석 기준
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_branch_id")
    private BaselineBranch baseBranch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pinned_commit_id")
    private BaselineCommit pinnedCommit;

    // 라인 취소 상태 전이
    public void cancel() {
        if (this.status == DecisionLineStatus.COMPLETED) {
            throw new IllegalStateException("cannot cancel a completed line");
        }
        this.status = DecisionLineStatus.CANCELLED;
    }

    // 라인 완료 상태 전이
    public void complete() {
        if (this.status == DecisionLineStatus.CANCELLED) {
            throw new IllegalStateException("cannot complete a cancelled line");
        }
        this.status = DecisionLineStatus.COMPLETED;
    }

    // 추가 노드 가능 여부 가드
    public void guardAppendable() {
        if (this.status == DecisionLineStatus.COMPLETED || this.status == DecisionLineStatus.CANCELLED) {
            throw new IllegalStateException("cannot append to a completed or cancelled decision line");
        }
    }

    // 해당 나이 구간의 존재 여부 확인
    public boolean hasDecisionAtAge(int ageYear) {
        if (this.getDecisionNodes() == null) return false;
        return this.getDecisionNodes().stream().anyMatch(d -> d.getAgeYear() == ageYear);
    }

    // 해당 나이 구간의 중복 방지
    public void ensureNoDecisionAtAge(int ageYear) {
        if (hasDecisionAtAge(ageYear)) {
            throw new IllegalStateException("a decision node already exists at this pivot age");
        }
    }
}
