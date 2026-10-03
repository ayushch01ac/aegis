ALTER TABLE routes
    ADD COLUMN rate_limit_algorithm VARCHAR(30),
    ADD COLUMN rate_limit_capacity INTEGER,
    ADD COLUMN rate_limit_window_seconds INTEGER,
    ADD COLUMN rate_limit_refill_rate INTEGER;
