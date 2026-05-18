package com.baseball.game.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "seat_sections", schema = "game_schema")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeatSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sectionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stadium_id")
    private Stadium stadium;

    private String sectionName;
    private Integer price;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
