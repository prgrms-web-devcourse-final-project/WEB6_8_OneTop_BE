/**
 * [ENTITY] BaselineCommit
 * - 브랜치 상의 단일 변경 단위를 나타내며, 부모 커밋과 메시지, 작성자를 보관
 * - 해당 커밋에 속한 BaselinePatch들이 실제 변경(버전 교체)을 기술
 */
package com.back.domain.node.entity;

import com.back.global.baseentity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "baseline_commits",
        indexes = {
                @Index(name = "idx_blcommit_branch", columnList = "branch_id"),
                @Index(name = "idx_blcommit_parent", columnList = "parent_commit_id")
        })
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class BaselineCommit extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private BaselineBranch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_commit_id")
    private BaselineCommit parentCommit;

    @Column(nullable = false)
    private Long authorUserId;

    @Column(length = 200)
    private String message;

    // 부모 커밋을 지정하여 새 커밋으로 초기화
    public static BaselineCommit newCommit(BaselineBranch branch, BaselineCommit parent, Long authorUserId, String message) {
        return BaselineCommit.builder()
                .branch(branch)
                .parentCommit(parent)
                .authorUserId(authorUserId)
                .message(message)
                .build();
    }
}
