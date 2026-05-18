package com.baseball.admin.repository;

import com.baseball.admin.domain.GameSeat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameSeatRepository extends JpaRepository<GameSeat, Long> {
}
