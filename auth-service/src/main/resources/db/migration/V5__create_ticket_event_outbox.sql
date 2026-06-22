-- V5: 예약 도메인 이벤트를 DB transaction과 함께 저장하는 Outbox

CREATE UNIQUE INDEX IF NOT EXISTS uq_reservations_idempotency_key
    ON ticket_schema.reservations (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE TABLE IF NOT EXISTS ticket_schema.event_outbox (
    outbox_id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    schema_version INTEGER NOT NULL,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id VARCHAR(120) NOT NULL,
    game_id BIGINT,
    destination VARCHAR(40) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    locked_at TIMESTAMPTZ,
    locked_by VARCHAR(120),
    published_at TIMESTAMPTZ,
    last_error VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_event_outbox_event_id UNIQUE (event_id),
    CONSTRAINT event_outbox_destination_check
        CHECK (destination IN ('TICKET_CONFIRM', 'DOMAIN_EVENTS')),
    CONSTRAINT event_outbox_status_check
        CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_event_outbox_publishable
    ON ticket_schema.event_outbox (status, next_attempt_at, outbox_id)
    WHERE status IN ('PENDING', 'FAILED');

CREATE INDEX IF NOT EXISTS idx_event_outbox_processing_lease
    ON ticket_schema.event_outbox (status, locked_at)
    WHERE status = 'PROCESSING';
