-- Seed data (manual run if needed)
-- Platform admin (store_id = 'platform')
INSERT IGNORE INTO users (id, store_id, username, password, role)
VALUES ('platform-admin-001', 'platform', 'admin', 'admin123', 'PLATFORM');
