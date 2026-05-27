package com.baseball.seat_lock_service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "game_seats", schema = "ticket_schema")
@Getter
@NoArgsConstructor
public class GameSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long gameSeatId;

    private Long gameId;
    private Long seatId;

    @Column(length = 30)
    private String status;

    private Integer price;
    private LocalDateTime updatedAt;

    public void lock() {
        this.status = "LOCKED";
        this.updatedAt = LocalDateTime.now();
    }

    public void unlock() {
        this.status = "AVAILABLE";
        this.updatedAt = LocalDateTime.now();
    }
}
