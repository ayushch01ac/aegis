ALTER TABLE routes
    ADD COLUMN retry_max_attempts INTEGER,
    ADD COLUMN retry_on_non_idempotent BOOLEAN,
    ADD COLUMN retry_initial_backoff_ms BIGINT,
    ADD COLUMN retry_backoff_multiplier DOUBLE PRECISION;
