ALTER TABLE ticket_schema.game_seats
    ALTER COLUMN price TYPE INTEGER
    USING price::INTEGER;
