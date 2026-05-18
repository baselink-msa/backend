package com.baseball.game.repository;

import com.baseball.game.domain.SeatSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatSectionRepository extends JpaRepository<SeatSection, Long> {

    List<SeatSection> findByStadium_StadiumIdOrderByPriceDesc(Long stadiumId);
}
