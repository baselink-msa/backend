package com.baseball.admin.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "seats", schema = "ticket_schema")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long seatId;

    private Long stadiumId;
    private Long sectionId;
    private String seatRow;
    private String seatNumber;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Seat(Long stadiumId, Long sectionId, String seatRow, String seatNumber) {
        this.stadiumId = stadiumId;
        this.sectionId = sectionId;
        this.seatRow = seatRow;
        this.seatNumber = seatNumber;
    }
}
