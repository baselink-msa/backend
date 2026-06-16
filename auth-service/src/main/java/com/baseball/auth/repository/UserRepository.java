package com.baseball.auth.repository;

import com.baseball.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Modifying
    @Query(value = "DELETE FROM order_schema.orders WHERE user_id = :userId", nativeQuery = true)
    int deleteOrdersByUserId(@Param("userId") Long userId);

    @Modifying
    @Query(value = """
            UPDATE ticket_schema.game_seats gs
            SET status = 'AVAILABLE', updated_at = now()
            WHERE EXISTS (
                SELECT 1
                FROM ticket_schema.reservations r
                WHERE r.user_id = :userId
                  AND r.game_id = gs.game_id
                  AND r.seat_id = gs.seat_id
                  AND r.status <> 'CANCELED'
            )
            """, nativeQuery = true)
    int releaseReservedSeatsByUserId(@Param("userId") Long userId);

    @Modifying
    @Query(value = "DELETE FROM ticket_schema.reservations WHERE user_id = :userId", nativeQuery = true)
    int deleteReservationsByUserId(@Param("userId") Long userId);
}
