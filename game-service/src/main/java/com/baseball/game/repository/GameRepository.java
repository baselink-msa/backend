package com.baseball.game.repository;

import com.baseball.game.domain.Game;
import com.baseball.game.domain.GameStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GameRepository extends JpaRepository<Game, Long> {

    // @EntityGraph: stadium을 함께 fetch → N+1 방지
    @EntityGraph(attributePaths = "stadium")
    List<Game> findAllByOrderByGameStartTimeDesc();

    @EntityGraph(attributePaths = "stadium")
    List<Game> findByStatusOrderByGameStartTimeDesc(GameStatus status);

    @EntityGraph(attributePaths = "stadium")
    Optional<Game> findByGameId(Long gameId);
}
