package com.baseball.ticket_worker_service.repository;

import com.baseball.ticket_worker_service.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
}