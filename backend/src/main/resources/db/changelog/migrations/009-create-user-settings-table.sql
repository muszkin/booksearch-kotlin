--liquibase formatted sql

--changeset booksearch:009-create-user-settings-table
CREATE TABLE user_settings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    setting_key TEXT NOT NULL,
    setting_value TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE UNIQUE INDEX idx_user_settings_user_key ON user_settings(user_id, setting_key);
CREATE INDEX idx_user_settings_user_id ON user_settings(user_id);
--rollback DROP TABLE user_settings;
