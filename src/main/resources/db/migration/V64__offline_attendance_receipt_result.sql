-- Preserve the original acknowledgement so retrying a lost response cannot silently
-- rebase subsequent local edits onto someone else's more recent server changes.
ALTER TABLE offline_attendance_receipts ADD COLUMN result_json TEXT;
