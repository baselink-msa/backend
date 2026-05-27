package com.baseball.ticket_service.repository;

import com.baseball.ticket_service.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    
    Optional<Reservation> findByIdempotencyKey(String idempotencyKey);

    List<Reservation> findByUserIdOrderByCreatedAtDesc(Long userId);
}