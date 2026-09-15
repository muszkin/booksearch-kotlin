--liquibase formatted sql

--changeset booksearch:015-add-book-description-metadata
ALTER TABLE books ADD COLUMN isbn TEXT;
ALTER TABLE books ADD COLUMN description_source TEXT;
ALTER TABLE books ADD COLUMN description_checked_at TEXT;
--rollback ALTER TABLE books DROP COLUMN isbn;
--rollback ALTER TABLE books DROP COLUMN description_source;
--rollback ALTER TABLE books DROP COLUMN description_checked_at;
