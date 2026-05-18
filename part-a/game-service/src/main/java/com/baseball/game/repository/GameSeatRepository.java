package com.baseball.game.repository;

import com.baseball.game.domain.GameSeat;
import com.baseball.game.domain.GameSeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GameSeatRepository extends JpaRepository<GameSeat, Long> {

    // 경기 좌석 + 좌석 마스터 + 구역을 한 번에 fetch join; sectionId·status는 null 전달 시 필터 미적용
    @Query("SELECT gs FROM GameSeat gs " +
            "JOIN FETCH gs.seat s " +
            "JOIN FETCH s.section sec " +
            "WHERE gs.game.gameId = :gameId " +
            "AND (:sectionId IS NULL OR sec.sectionId = :sectionId) " +
            "AND (:status IS NULL OR gs.status = :status) " +
            "ORDER BY gs.gameSeatId")
    List<GameSeat> findDetailedByGameId(@Param("gameId") Long gameId,
                                        @Param("sectionId") Long sectionId,
                                        @Param("status") GameSeatStatus status);
}
