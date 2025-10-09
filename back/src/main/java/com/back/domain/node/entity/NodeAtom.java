/**
 * [ENTITY] NodeAtom
 * - 노드 내용의 원천 단위를 나타내며, 불변 버전(NodeAtomVersion)의 루트가 됨
 * - 실제 렌더링은 NodeAtomVersion을 통해 이뤄지고, 여러 노드가 하나의 Atom을 공유할 수 있음
 */
package com.back.domain.node.entity;

import com.back.global.baseentity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "node_atoms")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class NodeAtom extends BaseEntity {

    @Column(length = 64)
    private String contentKey; // 동일 콘텐츠 식별(선택), 해시 키 등으로 활용 가능
}
