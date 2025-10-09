/**
 * [ENTITY] BaselineBranch
 * - 특정 BaseLine 위에서의 브랜치 개념을 나타내며, headCommit을 통해 최신 상태를 가리킴
 * - 브랜치 이름은 가독성을 위한 식별용이며 기능적으로는 headCommit으로 해석됨
 */
package com.back.domain.node.entity;

import com.back.global.baseentity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "baseline_branches",
        indexes = {
                @Index(name = "idx_blbranch_line", columnList = "base_line_id")
        })
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class BaselineBranch extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "base_line_id", nullable = false)
    private BaseLine baseLine;

    @Column(length = 64, nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "head_commit_id")
    private BaselineCommit headCommit;

    // 브랜치 헤드를 새로운 커밋으로 이동
    public void moveHeadTo(BaselineCommit commit) {
        this.headCommit = commit;
    }
}
