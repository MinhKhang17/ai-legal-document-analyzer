ALTER TABLE legal_tickets ADD COLUMN IF NOT EXISTS ticket_code VARCHAR(32);
ALTER TABLE legal_tickets ADD COLUMN IF NOT EXISTS source_user_message_id VARCHAR(255);
ALTER TABLE legal_tickets ADD COLUMN IF NOT EXISTS source_assistant_message_id VARCHAR(255);
ALTER TABLE legal_tickets ADD COLUMN IF NOT EXISTS recipient_type VARCHAR(32);
ALTER TABLE legal_tickets ADD COLUMN IF NOT EXISTS title VARCHAR(255);
ALTER TABLE legal_tickets ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE legal_tickets ADD COLUMN IF NOT EXISTS priority VARCHAR(32);
ALTER TABLE legal_tickets ADD COLUMN IF NOT EXISTS conversation_scope VARCHAR(64);
ALTER TABLE legal_tickets ADD COLUMN IF NOT EXISTS shared_document_ids_json TEXT;
ALTER TABLE legal_tickets ADD COLUMN IF NOT EXISTS focused_document_id VARCHAR(255);
CREATE UNIQUE INDEX IF NOT EXISTS uk_legal_tickets_ticket_code ON legal_tickets(ticket_code) WHERE ticket_code IS NOT NULL;

ALTER TABLE legal_ticket_messages ADD COLUMN IF NOT EXISTS sender_role VARCHAR(32);
ALTER TABLE legal_ticket_messages ADD COLUMN IF NOT EXISTS reply_to_message_id VARCHAR(255);
ALTER TABLE legal_ticket_messages ADD COLUMN IF NOT EXISTS edited_at TIMESTAMP;
ALTER TABLE legal_ticket_messages ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
DO $$ BEGIN
  ALTER TABLE legal_ticket_messages ADD CONSTRAINT fk_ticket_message_reply
    FOREIGN KEY (reply_to_message_id) REFERENCES legal_ticket_messages(id);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE TABLE IF NOT EXISTS ticket_context_snapshots (
  id VARCHAR(255) PRIMARY KEY, ticket_id VARCHAR(255) NOT NULL UNIQUE REFERENCES legal_tickets(id),
  user_question TEXT NOT NULL, assistant_answer TEXT, conversation_title VARCHAR(255),
  citation_snapshot_json TEXT, document_snapshot_json TEXT, selected_message_snapshot_json TEXT,
  content_hash VARCHAR(64) NOT NULL, created_at TIMESTAMP NOT NULL
);
CREATE TABLE IF NOT EXISTS ticket_attachments (
  id VARCHAR(255) PRIMARY KEY, owner_type VARCHAR(32) NOT NULL, owner_id VARCHAR(255) NOT NULL, ticket_id VARCHAR(255),
  uploaded_by_id BIGINT NOT NULL REFERENCES users(id), original_file_name VARCHAR(255) NOT NULL,
  stored_file_name VARCHAR(255) NOT NULL, mime_type VARCHAR(255) NOT NULL, size_bytes BIGINT NOT NULL,
  storage_key VARCHAR(255) NOT NULL UNIQUE, checksum VARCHAR(64) NOT NULL, scan_status VARCHAR(32) NOT NULL,
  upload_status VARCHAR(32) NOT NULL, created_at TIMESTAMP NOT NULL, deleted_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ticket_attachments_owner ON ticket_attachments(owner_type, owner_id);
CREATE TABLE IF NOT EXISTS conversation_shares (
  id VARCHAR(255) PRIMARY KEY, ticket_id VARCHAR(255) NOT NULL REFERENCES legal_tickets(id),
  session_id VARCHAR(255) NOT NULL, share_token_hash VARCHAR(64) NOT NULL UNIQUE,
  share_scope VARCHAR(64) NOT NULL, access_mode VARCHAR(32) NOT NULL, expires_at TIMESTAMP NOT NULL,
  revoked_at TIMESTAMP, created_by_id BIGINT NOT NULL REFERENCES users(id), created_at TIMESTAMP NOT NULL
);
CREATE TABLE IF NOT EXISTS ticket_audit_logs (
  id VARCHAR(255) PRIMARY KEY, ticket_id VARCHAR(255) NOT NULL,
  actor_id BIGINT NOT NULL REFERENCES users(id), action VARCHAR(64) NOT NULL,
  metadata_json TEXT, created_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_ticket_audit_ticket_created ON ticket_audit_logs(ticket_id, created_at);
