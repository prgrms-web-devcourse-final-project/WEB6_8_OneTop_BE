/**
 * [ENTITY] NodeAtomVersion
 * - 노드 내용의 불변 버전 스냅샷을 보관하며, 부모 버전과의 계보를 통해 변경 이력을 추적
 * - category/situation/decision/options/description/ageYear를 포함한 도메인 전체 스냅샷
 */
package com.back.domain.node.entity;

import com.back.global.baseentity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "node_atom_versions",
        indexes = {
                @Index(name = "idx_navers_atom", columnList = "atom_id"),
                @Index(name = "idx_navers_parent", columnList = "parent_version_id"),
                @Index(name = "idx_navers_age", columnList = "ageYear")
        })
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class NodeAtomVersion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "atom_id", nullable = false)
    private NodeAtom atom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_version_id")
    private NodeAtomVersion parentVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NodeCategory category;

    @Column(columnDefinition = "TEXT")
    private String situation;

    @Column(columnDefinition = "TEXT")
    private String decision;

    @Column(columnDefinition = "TEXT")
    private String optionsJson;  // 옵션 배열을 JSON 문자열로 저장

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Integer ageYear;

    @Column(length = 64)
    private String contentHash;

    // 옵션 JSON을 교체하여 새 버전을 생성
    public NodeAtomVersion forkWith(NodeCategory category, String situation, String decision,
                                    String optionsJson, String description, Integer ageYear, String contentHash) {
        return NodeAtomVersion.builder()
                .atom(this.atom)
                .parentVersion(this)
                .category(category != null ? category : this.category)
                .situation(situation != null ? situation : this.situation)
                .decision(decision != null ? decision : this.decision)
                .optionsJson(optionsJson != null ? optionsJson : this.optionsJson)
                .description(description != null ? description : this.description)
                .ageYear(ageYear != null ? ageYear : this.ageYear)
                .contentHash(contentHash)
                .build();
    }
}
