-- Align PostgreSQL schema with entity fields added after the 2026-07-19 migrations.
-- Statements are idempotent so this script is safe for databases where Hibernate
-- may already have created one or more of these columns.

-- Chat session sharing
ALTER TABLE chat_sessions
    ADD COLUMN IF NOT EXISTS share_access_level VARCHAR(255);

UPDATE chat_sessions
SET share_access_level = 'RESTRICTED'
WHERE share_access_level IS NULL;

ALTER TABLE chat_sessions
    ALTER COLUMN share_access_level SET DEFAULT 'RESTRICTED',
    ALTER COLUMN share_access_level SET NOT NULL;

-- Knowledge ingestion metadata
ALTER TABLE knowledge_base_versions
    ADD COLUMN IF NOT EXISTS source_relative_path TEXT,
    ADD COLUMN IF NOT EXISTS source_file_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS ingest_source VARCHAR(100),
    ADD COLUMN IF NOT EXISTS neo4j_document_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS chunk_count INTEGER,
    ADD COLUMN IF NOT EXISTS source_version_label VARCHAR(255),
    ADD COLUMN IF NOT EXISTS effective_date VARCHAR(255);

-- Expert revenue fields on legal tickets. Existing tickets are retained and
-- backfilled before the NOT NULL constraint is applied.
ALTER TABLE legal_tickets
    ADD COLUMN IF NOT EXISTS consultation_fee NUMERIC(19, 2),
    ADD COLUMN IF NOT EXISTS expert_payment_status VARCHAR(255),
    ADD COLUMN IF NOT EXISTS expert_paid_at TIMESTAMP;

UPDATE legal_tickets
SET consultation_fee = 0
WHERE consultation_fee IS NULL;

UPDATE legal_tickets
SET expert_payment_status = 'UNPAID'
WHERE expert_payment_status IS NULL;

ALTER TABLE legal_tickets
    ALTER COLUMN consultation_fee SET DEFAULT 0,
    ALTER COLUMN expert_payment_status SET DEFAULT 'UNPAID',
    ALTER COLUMN expert_payment_status SET NOT NULL;

-- Forgot-password state
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS forgot_password_token VARCHAR(64),
    ADD COLUMN IF NOT EXISTS forgot_password_token_expiry TIMESTAMP,
    ADD COLUMN IF NOT EXISTS forgot_password_requested_at TIMESTAMP;
