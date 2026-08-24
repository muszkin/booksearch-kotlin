--liquibase formatted sql

--changeset booksearch:014-create-search-jobs-table
CREATE TABLE search_jobs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    query TEXT NOT NULL,
    language TEXT NOT NULL,
    format TEXT NOT NULL,
    max_pages INTEGER NOT NULL,
    status TEXT NOT NULL DEFAULT 'queued',
    results TEXT,
    total_results INTEGER,
    error TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_search_jobs_user_id ON search_jobs(user_id);
CREATE INDEX idx_search_jobs_status ON search_jobs(status);
--rollback DROP TABLE search_jobs;
