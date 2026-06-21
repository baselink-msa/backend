package com.baseball.ticket_service.service;

import com.baseball.ticket_service.entity.OutboxDestination;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TicketEventOutboxClaimService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public List<ClaimedOutboxEvent> claim(
            String instanceId,
            int batchSize,
            int maxAttempts) {
        return jdbcTemplate.query("""
                WITH candidates AS (
                    SELECT outbox_id
                    FROM ticket_schema.event_outbox
                    WHERE status IN ('PENDING', 'FAILED')
                      AND next_attempt_at <= now()
                      AND attempts < ?
                    ORDER BY outbox_id
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                UPDATE ticket_schema.event_outbox o
                SET status = 'PROCESSING',
                    locked_at = now(),
                    locked_by = ?,
                    attempts = attempts + 1
                FROM candidates c
                WHERE o.outbox_id = c.outbox_id
                RETURNING o.outbox_id, o.destination, o.payload::text, o.attempts
                """,
                (rs, rowNum) -> new ClaimedOutboxEvent(
                        rs.getLong("outbox_id"),
                        OutboxDestination.valueOf(rs.getString("destination")),
                        rs.getString("payload"),
                        rs.getInt("attempts")),
                maxAttempts,
                batchSize,
                instanceId);
    }

    @Transactional
    public void markPublished(Long outboxId) {
        jdbcTemplate.update("""
                UPDATE ticket_schema.event_outbox
                SET status = 'PUBLISHED',
                    published_at = now(),
                    locked_at = NULL,
                    locked_by = NULL,
                    last_error = NULL
                WHERE outbox_id = ?
                  AND status = 'PROCESSING'
                """, outboxId);
    }

    @Transactional
    public void markFailed(Long outboxId, Instant nextAttemptAt, String lastError) {
        jdbcTemplate.update("""
                UPDATE ticket_schema.event_outbox
                SET status = 'FAILED',
                    next_attempt_at = ?,
                    locked_at = NULL,
                    locked_by = NULL,
                    last_error = ?
                WHERE outbox_id = ?
                  AND status = 'PROCESSING'
                """,
                Timestamp.from(nextAttemptAt),
                truncate(lastError, 1000),
                outboxId);
    }

    @Transactional
    public int recoverExpiredLeases(Instant leaseExpiredBefore) {
        return jdbcTemplate.update("""
                UPDATE ticket_schema.event_outbox
                SET status = 'FAILED',
                    next_attempt_at = now(),
                    locked_at = NULL,
                    locked_by = NULL,
                    last_error = 'Publisher lease expired before completion'
                WHERE status = 'PROCESSING'
                  AND locked_at < ?
                """, Timestamp.from(leaseExpiredBefore));
    }

    public long countPending(int maxAttempts) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM ticket_schema.event_outbox
                WHERE status IN ('PENDING', 'FAILED')
                  AND attempts < ?
                """, Long.class, maxAttempts);
        return count == null ? 0 : count;
    }

    public long oldestPendingSeconds(int maxAttempts) {
        Long seconds = jdbcTemplate.queryForObject("""
                SELECT COALESCE(
                    EXTRACT(EPOCH FROM (now() - min(created_at)))::bigint,
                    0
                )
                FROM ticket_schema.event_outbox
                WHERE status IN ('PENDING', 'FAILED')
                  AND attempts < ?
                """, Long.class, maxAttempts);
        return seconds == null ? 0 : Math.max(0, seconds);
    }

    public long countTerminalFailed(int maxAttempts) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM ticket_schema.event_outbox
                WHERE status = 'FAILED'
                  AND attempts >= ?
                """, Long.class, maxAttempts);
        return count == null ? 0 : count;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "Unknown publisher error";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
