ALTER TABLE chat_messages
    ADD COLUMN IF NOT EXISTS drafting_response_json TEXT;
