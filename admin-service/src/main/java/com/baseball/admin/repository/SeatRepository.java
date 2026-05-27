package com.baseball.admin.repository;

import com.baseball.admin.domain.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findBySectionId(Long sectionId);
    List<Seat> findByStadiumId(Long stadiumId);
}
