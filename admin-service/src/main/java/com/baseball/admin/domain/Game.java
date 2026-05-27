package com.baseball.admin.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "games", schema = "game_schema")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long gameId;

    private String homeTeamName;
    private String awayTeamName;

    // MSA 원칙: 다른 도메인 참조는 FK 연관 대신 ID 값으로 보관
    private Long stadiumId;

    private LocalDateTime gameStartTime;
    private LocalDateTime ticketOpenTime;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private GameStatus status;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Game(String homeTeamName, String awayTeamName, Long stadiumId,
                 LocalDateTime gameStartTime, LocalDateTime ticketOpenTime, GameStatus status) {
        this.homeTeamName = homeTeamName;
        this.awayTeamName = awayTeamName;
        this.stadiumId = stadiumId;
        this.gameStartTime = gameStartTime;
        this.ticketOpenTime = ticketOpenTime;
        this.status = status;
    }

    public void update(String homeTeamName, String awayTeamName, Long stadiumId,
                       LocalDateTime gameStartTime, LocalDateTime ticketOpenTime) {
        this.homeTeamName = homeTeamName;
        this.awayTeamName = awayTeamName;
        this.stadiumId = stadiumId;
        this.gameStartTime = gameStartTime;
        this.ticketOpenTime = ticketOpenTime;
    }

    public void changeStatus(GameStatus status) {
        this.status = status;
    }
}
