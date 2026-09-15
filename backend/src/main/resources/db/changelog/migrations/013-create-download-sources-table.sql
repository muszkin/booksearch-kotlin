--liquibase formatted sql

--changeset booksearch:013-create-download-sources-table
CREATE TABLE download_sources (
    book_md5 TEXT PRIMARY KEY NOT NULL,
    mirror TEXT NOT NULL,
    torrent_url TEXT NOT NULL,
    file_level1 TEXT NOT NULL,
    file_level2 TEXT,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (book_md5) REFERENCES books(md5) ON DELETE CASCADE
);
--rollback DROP TABLE download_sources;
