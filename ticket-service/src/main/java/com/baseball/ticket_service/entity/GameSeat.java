package com.baseball.ticket_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "game_seats", schema = "ticket_schema")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "game_seat_id")
    private Long gameSeatId;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "seat_id", nullable = false)
    private Long seatId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private GameSeatStatus status;

    @Column(name = "price", nullable = false)
    private BigDecimal price;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum GameSeatStatus {
        AVAILABLE, SOLD, BLOCKED, LOCKED
    }

    public void markSold() {
        this.status = GameSeatStatus.SOLD;
        this.updatedAt = LocalDateTime.now();
    }

    public void markAvailable() {
        this.status = GameSeatStatus.AVAILABLE;
        this.updatedAt = LocalDateTime.now();
    }
}
