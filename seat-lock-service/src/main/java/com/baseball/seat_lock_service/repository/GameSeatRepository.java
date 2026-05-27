package com.baseball.seat_lock_service.repository;

import com.baseball.seat_lock_service.entity.GameSeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GameSeatRepository extends JpaRepository<GameSeat, Long> {
    Optional<GameSeat> findByGameIdAndSeatId(Long gameId, Long seatId);
}
