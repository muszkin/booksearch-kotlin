--liquibase formatted sql

--changeset booksearch:007-create-mirrors-table
CREATE TABLE mirrors (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    domain TEXT UNIQUE NOT NULL,
    base_url TEXT NOT NULL,
    is_working INTEGER NOT NULL DEFAULT 0,
    last_checked_at TEXT,
    response_time_ms INTEGER
);
--rollback DROP TABLE mirrors;
