package com.baseball.ticket_service.repository;

import com.baseball.ticket_service.entity.EventOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventOutboxRepository extends JpaRepository<EventOutbox, Long> {
}
