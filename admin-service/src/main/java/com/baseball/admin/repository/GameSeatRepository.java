package com.baseball.admin.repository;

import com.baseball.admin.domain.GameSeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GameSeatRepository extends JpaRepository<GameSeat, Long> {
    List<GameSeat> findByGameId(Long gameId);
    void deleteByGameId(Long gameId);
}
