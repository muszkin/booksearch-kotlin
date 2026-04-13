--liquibase formatted sql

--changeset booksearch:008-create-download-jobs-table
CREATE TABLE download_jobs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    book_md5 TEXT NOT NULL,
    format TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'queued',
    progress INTEGER NOT NULL DEFAULT 0,
    file_path TEXT,
    error TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (book_md5) REFERENCES books(md5)
);

CREATE INDEX idx_download_jobs_user_id ON download_jobs(user_id);
CREATE INDEX idx_download_jobs_status ON download_jobs(status);
--rollback DROP TABLE download_jobs;
