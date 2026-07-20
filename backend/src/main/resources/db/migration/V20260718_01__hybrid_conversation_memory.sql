-- Idempotent migration for hybrid conversation memory.
-- The current application still uses Hibernate ddl-auto=update; this script is
-- intentionally safe to run manually and ready for a future Flyway rollout.
ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS conversation_summary_json TEXT;
ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS summary_through_message_id VARCHAR(255);
ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS active_document_ids_json TEXT;
ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS focused_document_id VARCHAR(255);
ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS message_attached_document_ids_json TEXT;
ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS conversation_user_role VARCHAR(100);
ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS conversation_mode VARCHAR(100);
ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS memory_updated_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_chat_messages_session_status_created
    ON chat_messages (chat_session_id, status, created_at DESC);
