--liquibase formatted sql

--changeset booksearch:005-create-books-table
CREATE TABLE books (
    md5 TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    author TEXT,
    language TEXT,
    format TEXT,
    file_size TEXT,
    source_url TEXT,
    detail_url TEXT,
    cover_url TEXT,
    publisher TEXT,
    year TEXT,
    description TEXT,
    indexed_at TEXT NOT NULL
);

CREATE INDEX idx_books_title ON books(lower(title));
CREATE INDEX idx_books_author ON books(lower(author));
--rollback DROP TABLE books;
