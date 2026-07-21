ALTER TABLE chat_sessions
ADD COLUMN IF NOT EXISTS share_access_level VARCHAR(50);
