/**
 * [ENTITY] BaselinePatch
 * - 특정 커밋에서 한 ageYear 구간의 NodeAtomVersion 교체(before → after)를 기록
 * - 커밋 체인을 따라 올라가며 ageYear의 최종 버전을 해석하는 데 사용
 */
package com.back.domain.node.entity;

import com.back.global.baseentity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "baseline_patches",
        indexes = {
                @Index(name = "idx_blpatch_commit", columnList = "commit_id"),
                @Index(name = "idx_blpatch_age", columnList = "ageYear")
        })
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class BaselinePatch extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commit_id", nullable = false)
    private BaselineCommit commit;

    @Column(nullable = false)
    private Integer ageYear;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "before_version_id")
    private NodeAtomVersion beforeVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "after_version_id", nullable = false)
    private NodeAtomVersion afterVersion;

    // 단일 ageYear 구간의 버전 교체를 패치로 생성
    public static BaselinePatch of(BaselineCommit commit, Integer ageYear, NodeAtomVersion before, NodeAtomVersion after) {
        return BaselinePatch.builder()
                .commit(commit)
                .ageYear(ageYear)
                .beforeVersion(before)
                .afterVersion(after)
                .build();
    }
}
