ALTER TABLE attendances ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Keys are available only through an authenticated, non-cacheable endpoint.
-- No encryption key is persisted by the browser.
CREATE TABLE offline_device_keys (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id UUID NOT NULL,
    encryption_key VARCHAR(44) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(user_id, device_id)
);
CREATE TABLE offline_attendance_receipts (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    operation_id UUID NOT NULL,
    class_id BIGINT NOT NULL REFERENCES classes(id) ON DELETE CASCADE,
    payload_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(user_id, operation_id)
);
