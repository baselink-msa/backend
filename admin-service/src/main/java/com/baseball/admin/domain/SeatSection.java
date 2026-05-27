package com.baseball.admin.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
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

    private Long stadiumId;
    private String sectionName;
    private Integer price;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private SeatSection(Long stadiumId, String sectionName, Integer price) {
        this.stadiumId = stadiumId;
        this.sectionName = sectionName;
        this.price = price;
    }

    public void update(String sectionName, Integer price) {
        this.sectionName = sectionName;
        this.price = price;
    }
}
