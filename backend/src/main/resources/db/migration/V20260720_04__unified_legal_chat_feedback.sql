-- Additive, idempotent schema changes for unified legal chat feedback.
-- This migration intentionally preserves all existing chat and feedback data.

ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS conversation_mode VARCHAR(100);
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS resolved_mode VARCHAR(32);
CREATE INDEX IF NOT EXISTS idx_chat_messages_resolved_mode
    ON chat_messages (resolved_mode);

CREATE TABLE IF NOT EXISTS chat_message_feedbacks (
    id VARCHAR(255) PRIMARY KEY,
    chat_message_id VARCHAR(255) NOT NULL,
    chat_session_id VARCHAR(255),
    user_id BIGINT,
    feedback_type VARCHAR(32),
    rating VARCHAR(32),
    reasons TEXT,
    reason TEXT,
    comment TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE chat_message_feedbacks ADD COLUMN IF NOT EXISTS chat_session_id VARCHAR(255);
ALTER TABLE chat_message_feedbacks ADD COLUMN IF NOT EXISTS user_id BIGINT;
ALTER TABLE chat_message_feedbacks ADD COLUMN IF NOT EXISTS feedback_type VARCHAR(32);
ALTER TABLE chat_message_feedbacks ADD COLUMN IF NOT EXISTS reason TEXT;

UPDATE chat_message_feedbacks feedback
SET chat_session_id = message.chat_session_id,
    user_id = message.user_id,
    feedback_type = CASE feedback.rating
        WHEN 'THUMBS_UP' THEN 'LIKE'
        WHEN 'THUMBS_DOWN' THEN 'DISLIKE'
        ELSE feedback.feedback_type
    END
FROM chat_messages message
WHERE feedback.chat_message_id = message.id
  AND (feedback.chat_session_id IS NULL OR feedback.user_id IS NULL OR feedback.feedback_type IS NULL);

ALTER TABLE chat_message_feedbacks DROP CONSTRAINT IF EXISTS uk_chat_message_feedback_message;
CREATE UNIQUE INDEX IF NOT EXISTS uk_chat_message_feedback_message_user
    ON chat_message_feedbacks (chat_message_id, user_id);
CREATE INDEX IF NOT EXISTS idx_chat_feedback_created_at
    ON chat_message_feedbacks (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_chat_feedback_type_created_at
    ON chat_message_feedbacks (feedback_type, created_at DESC);

ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verification_last_used_token VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verification_token_used_at TIMESTAMP;
CREATE INDEX IF NOT EXISTS idx_users_email_verification_last_used_token
    ON users (email_verification_last_used_token);

ALTER TABLE refund_requests ADD COLUMN IF NOT EXISTS confirmation_used_token_hash VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_refund_confirmation_used_token_hash
    ON refund_requests (confirmation_used_token_hash);
