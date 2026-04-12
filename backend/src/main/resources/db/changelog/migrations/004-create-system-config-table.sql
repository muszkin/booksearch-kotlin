--liquibase formatted sql

--changeset booksearch:004-create-system-config-table
CREATE TABLE system_config (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

INSERT INTO system_config (key, value) VALUES ('registration_enabled', 'true');
--rollback DROP TABLE system_config;
