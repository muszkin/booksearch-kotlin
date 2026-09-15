--liquibase formatted sql

--changeset booksearch:014-create-translation-chapters-table
CREATE TABLE translation_chapters (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    job_id TEXT NOT NULL,
    chapter_index INTEGER NOT NULL,
    href TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'queued',
    attempts INTEGER NOT NULL DEFAULT 0,
    input_tokens INTEGER NOT NULL DEFAULT 0,
    output_tokens INTEGER NOT NULL DEFAULT 0,
    error TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    completed_at TEXT,
    FOREIGN KEY (job_id) REFERENCES translation_jobs(id)
);

CREATE UNIQUE INDEX idx_translation_chapters_job_index ON translation_chapters(job_id, chapter_index);
CREATE INDEX idx_translation_chapters_job_id ON translation_chapters(job_id);
--rollback DROP TABLE translation_chapters;
