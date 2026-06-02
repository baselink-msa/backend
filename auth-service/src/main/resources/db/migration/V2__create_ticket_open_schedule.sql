-- KEDA pre-scaling schedule derived from ticket opening times.

CREATE TABLE IF NOT EXISTS ticket_schema.ticket_open_schedule (
    game_id BIGINT PRIMARY KEY REFERENCES game_schema.games(game_id) ON DELETE CASCADE,
    open_at TIMESTAMP NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'scheduled',
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ticket_open_schedule_status_open_at
    ON ticket_schema.ticket_open_schedule (status, open_at);
