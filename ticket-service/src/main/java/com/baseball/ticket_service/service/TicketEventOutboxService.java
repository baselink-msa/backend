package com.baseball.ticket_service.service;

import com.baseball.ticket_service.entity.EventOutbox;
import com.baseball.ticket_service.entity.OutboxDestination;
import com.baseball.ticket_service.event.TicketEventEnvelope;
import com.baseball.ticket_service.repository.EventOutboxRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketEventOutboxService {

    private final EventOutboxRepository eventOutboxRepository;
    private final ObjectMapper objectMapper;

    public EventOutbox appendDomainEvent(TicketEventEnvelope event) {
        return append(
                event.eventId(),
                event.eventType().name(),
                event.schemaVersion(),
                event.aggregateType(),
                event.aggregateId(),
                event.gameId(),
                OutboxDestination.DOMAIN_EVENTS,
                objectMapper.valueToTree(event),
                event.occurredAt());
    }

    public EventOutbox appendTicketConfirmationCommand(
            Long reservationId,
            Long userId,
            Long gameId,
            Long seatId,
            String lockId) {
        Instant occurredAt = Instant.now();
        JsonNode payload = objectMapper.valueToTree(Map.of(
                "reservationId", reservationId,
                "userId", userId,
                "gameId", gameId,
                "seatId", seatId,
                "lockId", lockId));
        return append(
                UUID.randomUUID(),
                "TICKET_CONFIRMATION_REQUESTED",
                TicketEventEnvelope.CURRENT_SCHEMA_VERSION,
                TicketEventEnvelope.RESERVATION_AGGREGATE,
                reservationId.toString(),
                gameId,
                OutboxDestination.TICKET_CONFIRM,
                payload,
                occurredAt);
    }

    private EventOutbox append(
            UUID eventId,
            String eventType,
            int schemaVersion,
            String aggregateType,
            String aggregateId,
            Long gameId,
            OutboxDestination destination,
            JsonNode payload,
            Instant occurredAt) {
        return eventOutboxRepository.save(EventOutbox.builder()
                .eventId(eventId)
                .eventType(eventType)
                .schemaVersion(schemaVersion)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .gameId(gameId)
                .destination(destination)
                .payload(payload)
                .status(EventOutbox.OutboxStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(occurredAt)
                .createdAt(Instant.now())
                .build());
    }
}
