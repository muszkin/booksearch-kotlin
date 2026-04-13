--liquibase formatted sql

--changeset booksearch:006-create-user-library-table
CREATE TABLE user_library (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    book_md5 TEXT NOT NULL,
    format TEXT NOT NULL,
    file_path TEXT,
    added_at TEXT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (book_md5) REFERENCES books(md5)
);

CREATE UNIQUE INDEX idx_user_library_unique ON user_library(user_id, book_md5, format);
CREATE INDEX idx_user_library_user_id ON user_library(user_id);
CREATE INDEX idx_user_library_user_book ON user_library(user_id, book_md5);
--rollback DROP TABLE user_library;
