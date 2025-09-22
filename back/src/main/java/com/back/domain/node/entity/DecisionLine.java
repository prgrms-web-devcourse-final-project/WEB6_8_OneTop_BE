/**
 * [ENTITY] 사용자의 선택 분기(Decision Node)들의 연결
 * - 같은 BaseLine을 따라가며 길이를 넘지 않도록 제한
 */
package com.back.domain.node.entity;

import com.back.domain.user.entity.User;
import com.back.global.baseentity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "decision_lines")
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


    public void cancel() {
        if (this.status == DecisionLineStatus.COMPLETED) {
            throw new IllegalStateException("cannot cancel a completed line");
        }
        this.status = DecisionLineStatus.CANCELLED;
    }

    public void complete() {
        if (this.status == DecisionLineStatus.CANCELLED) {
            throw new IllegalStateException("cannot complete a cancelled line");
        }
        this.status = DecisionLineStatus.COMPLETED;
    }


    public void guardAppendable() {
        if (this.status == DecisionLineStatus.COMPLETED || this.status == DecisionLineStatus.CANCELLED) {
            throw new IllegalStateException("cannot append to a completed or cancelled decision line");
        }
    }

    public boolean hasDecisionAtAge(int ageYear) {
        if (this.getDecisionNodes() == null) return false;
        return this.getDecisionNodes().stream().anyMatch(d -> d.getAgeYear() == ageYear);
    }

    public void ensureNoDecisionAtAge(int ageYear) {
        if (hasDecisionAtAge(ageYear)) {
            throw new IllegalStateException("a decision node already exists at this pivot age");
        }
    }
}
