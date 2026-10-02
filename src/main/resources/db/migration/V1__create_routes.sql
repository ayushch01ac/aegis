-- Route definitions are looked up by unique name when creating/updating configuration,
-- and by primary key on administrative CRUD. The unique index on name supports both
-- uniqueness enforcement and name-based lookup without a sequential scan.
CREATE TABLE routes (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    base_url VARCHAR(500) NOT NULL,
    timeout_ms INTEGER NOT NULL,
    priority VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX ux_routes_name ON routes (name);
