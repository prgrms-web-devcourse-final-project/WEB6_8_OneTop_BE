package com.back.domain.node.entity;

import com.back.domain.user.entity.User;
import com.back.global.baseentity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 사용자의 현재 삶의 베이스라인을 나타내는 엔티티.
 * 각 사용자는 하나의 베이스라인을 가집니다.
 */
@Entity
@Table(name = "base_lines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BaseLine extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}