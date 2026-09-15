--liquibase formatted sql

--changeset booksearch:013-create-translation-jobs-table
CREATE TABLE translation_jobs (
    id TEXT PRIMARY KEY,
    user_id INTEGER NOT NULL,
    source_library_entry_id INTEGER NOT NULL,
    source_book_md5 TEXT,
    source_file_path TEXT,
    model_id TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'queued',
    total_chapters INTEGER NOT NULL DEFAULT 0,
    completed_chapters INTEGER NOT NULL DEFAULT 0,
    failed_chapter_index INTEGER,
    estimated_input_tokens INTEGER NOT NULL DEFAULT 0,
    actual_input_tokens INTEGER NOT NULL DEFAULT 0,
    actual_output_tokens INTEGER NOT NULL DEFAULT 0,
    workspace_path TEXT,
    output_library_entry_id INTEGER,
    error TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    completed_at TEXT,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (source_library_entry_id) REFERENCES user_library(id),
    FOREIGN KEY (output_library_entry_id) REFERENCES user_library(id)
);

CREATE INDEX idx_translation_jobs_user_id ON translation_jobs(user_id);
CREATE INDEX idx_translation_jobs_status ON translation_jobs(status);
CREATE INDEX idx_translation_jobs_source_library_entry_id ON translation_jobs(source_library_entry_id);
--rollback DROP TABLE translation_jobs;
