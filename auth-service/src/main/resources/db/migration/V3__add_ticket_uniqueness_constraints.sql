DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uq_seats_stadium_section_row_number'
          AND conrelid = 'ticket_schema.seats'::regclass
    ) THEN
        ALTER TABLE ticket_schema.seats
            ADD CONSTRAINT uq_seats_stadium_section_row_number
            UNIQUE (stadium_id, section_id, seat_row, seat_number);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uq_game_seats_game_seat'
          AND conrelid = 'ticket_schema.game_seats'::regclass
    ) THEN
        ALTER TABLE ticket_schema.game_seats
            ADD CONSTRAINT uq_game_seats_game_seat
            UNIQUE (game_id, seat_id);
    END IF;
END $$;
