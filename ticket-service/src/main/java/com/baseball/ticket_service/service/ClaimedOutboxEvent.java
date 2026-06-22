package com.baseball.ticket_service.service;

import com.baseball.ticket_service.entity.OutboxDestination;

public record ClaimedOutboxEvent(
        Long outboxId,
        OutboxDestination destination,
        String payload,
        int attempts) {
}
