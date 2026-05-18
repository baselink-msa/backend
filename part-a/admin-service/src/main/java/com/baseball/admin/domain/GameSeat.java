package com.baseball.admin.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "game_seats", schema = "ticket_schema")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long gameSeatId;

    private Long gameId;
    private Long seatId;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private GameSeatStatus status;

    private Integer price;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Builder
    private GameSeat(Long gameId, Long seatId, GameSeatStatus status, Integer price) {
        this.gameId = gameId;
        this.seatId = seatId;
        this.status = status;
        this.price = price;
    }
}
