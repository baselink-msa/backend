-- V5: 예약 도메인 이벤트를 DB transaction과 함께 저장하는 Outbox

-- 유니크 제약 도입 전에 기존 dev/복구 데이터의 중복 키를 안전하게 정리합니다.
-- 예약 행은 삭제하지 않고 대표 행 하나만 idempotency_key를 유지합니다.
-- 확정된 예약을 가장 우선하고, 같은 상태라면 가장 최근 행을 대표로 선택합니다.
WITH ranked_reservations AS (
    SELECT reservation_id,
           ROW_NUMBER() OVER (
               PARTITION BY idempotency_key
               ORDER BY CASE status
                            WHEN 'CONFIRMED' THEN 1
                            WHEN 'PENDING' THEN 2
                            WHEN 'CANCELED' THEN 3
                            WHEN 'FAILED' THEN 4
                            ELSE 5
                        END,
                        created_at DESC NULLS LAST,
                        reservation_id DESC
           ) AS duplicate_rank
    FROM ticket_schema.reservations
    WHERE idempotency_key IS NOT NULL
)
UPDATE ticket_schema.reservations AS reservation
SET idempotency_key = NULL
FROM ranked_reservations AS ranked
WHERE reservation.reservation_id = ranked.reservation_id
  AND ranked.duplicate_rank > 1;

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
