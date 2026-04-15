--liquibase formatted sql

--changeset booksearch:012-create-request-logs-table
CREATE TABLE request_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    method TEXT NOT NULL,
    path TEXT NOT NULL,
    status_code INTEGER NOT NULL,
    duration_ms INTEGER NOT NULL,
    request_headers TEXT,
    response_headers TEXT,
    request_id TEXT,
    user_id INTEGER,
    created_at TEXT NOT NULL DEFAULT(datetime('now'))
);

CREATE INDEX idx_request_logs_created_at ON request_logs(created_at);
CREATE INDEX idx_request_logs_method_path ON request_logs(method, path);
--rollback DROP TABLE request_logs;
