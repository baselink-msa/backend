package com.baseball.admin.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "waiting_room_policies", schema = "ticket_schema")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WaitingRoomPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long policyId;

    private Long gameId;
    private Integer maxEnterPerMinute;
    private Integer tokenTtlSeconds;
    private Boolean enabled;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Builder
    private WaitingRoomPolicy(Long gameId, Integer maxEnterPerMinute,
                              Integer tokenTtlSeconds, Boolean enabled) {
        this.gameId = gameId;
        this.maxEnterPerMinute = maxEnterPerMinute;
        this.tokenTtlSeconds = tokenTtlSeconds;
        this.enabled = enabled;
    }

    public void update(Integer maxEnterPerMinute, Integer tokenTtlSeconds, Boolean enabled) {
        this.maxEnterPerMinute = maxEnterPerMinute;
        this.tokenTtlSeconds = tokenTtlSeconds;
        this.enabled = enabled;
    }
}
