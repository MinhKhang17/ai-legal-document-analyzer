ALTER TABLE subscription_plans ADD COLUMN IF NOT EXISTS storage_limit_mb INTEGER;
ALTER TABLE subscription_plans ADD COLUMN IF NOT EXISTS max_file_size_mb INTEGER;
ALTER TABLE subscription_plans ADD COLUMN IF NOT EXISTS max_attached_documents_per_session INTEGER;
ALTER TABLE subscription_plans ADD COLUMN IF NOT EXISTS allow_system_error_ticket BOOLEAN DEFAULT TRUE;
ALTER TABLE subscription_plans ADD COLUMN IF NOT EXISTS allow_query_error_ticket BOOLEAN DEFAULT TRUE;
ALTER TABLE subscription_plans ADD COLUMN IF NOT EXISTS allow_contact_expert_ticket BOOLEAN DEFAULT FALSE;

UPDATE subscription_plans SET
  storage_limit_mb = CASE plan_type WHEN 'FREE' THEN 50 WHEN 'STANDARD' THEN 1024 WHEN 'PREMIUM' THEN 5120 ELSE COALESCE(storage_limit_mb, 0) END,
  max_file_size_mb = CASE plan_type WHEN 'FREE' THEN 10 WHEN 'STANDARD' THEN 25 WHEN 'PREMIUM' THEN 50 ELSE COALESCE(max_file_size_mb, 0) END,
  max_attached_documents_per_session = CASE plan_type WHEN 'FREE' THEN 1 WHEN 'STANDARD' THEN 5 WHEN 'PREMIUM' THEN 15 ELSE COALESCE(max_attached_documents_per_session, 0) END,
  allow_system_error_ticket = TRUE,
  allow_query_error_ticket = TRUE,
  allow_contact_expert_ticket = CASE WHEN plan_type = 'PREMIUM' THEN TRUE ELSE FALSE END;

CREATE UNIQUE INDEX IF NOT EXISTS uk_subscription_plan_type_lower ON subscription_plans (lower(plan_type));

ALTER TABLE customer_plans ADD COLUMN IF NOT EXISTS scheduled_subscription_plan_id BIGINT REFERENCES subscription_plans(id);
ALTER TABLE customer_plans ADD COLUMN IF NOT EXISTS plan_change_effective_at TIMESTAMP;

ALTER TABLE legal_tickets ALTER COLUMN workspace_id DROP NOT NULL;

ALTER TABLE refund_requests ADD COLUMN IF NOT EXISTS legal_ticket_id VARCHAR(255) REFERENCES legal_tickets(id);
ALTER TABLE refund_requests ADD COLUMN IF NOT EXISTS bank_name VARCHAR(255);
ALTER TABLE refund_requests ADD COLUMN IF NOT EXISTS account_number VARCHAR(100);
ALTER TABLE refund_requests ADD COLUMN IF NOT EXISTS account_holder_name VARCHAR(255);
ALTER TABLE refund_requests ADD COLUMN IF NOT EXISTS invoice_id VARCHAR(100);
ALTER TABLE refund_requests ADD COLUMN IF NOT EXISTS confirmation_token_hash VARCHAR(64);
ALTER TABLE refund_requests ADD COLUMN IF NOT EXISTS confirmation_expires_at TIMESTAMP;
ALTER TABLE refund_requests ADD COLUMN IF NOT EXISTS email_confirmed_at TIMESTAMP;
CREATE UNIQUE INDEX IF NOT EXISTS uk_refund_confirmation_token_hash
  ON refund_requests (confirmation_token_hash) WHERE confirmation_token_hash IS NOT NULL;

ALTER TABLE knowledge_base_versions ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE knowledge_base_versions ADD COLUMN IF NOT EXISTS original_file_name VARCHAR(255);
ALTER TABLE knowledge_base_versions ADD COLUMN IF NOT EXISTS source_content_type VARCHAR(255);
ALTER TABLE knowledge_base_versions ADD COLUMN IF NOT EXISTS source_file_size BIGINT;
ALTER TABLE knowledge_base_versions ADD COLUMN IF NOT EXISTS source_storage_path TEXT;
ALTER TABLE knowledge_base_versions ADD COLUMN IF NOT EXISTS source_uploaded_at TIMESTAMP;
ALTER TABLE knowledge_base_versions ADD COLUMN IF NOT EXISTS ingest_notified_at TIMESTAMP;

CREATE TABLE IF NOT EXISTS system_notifications (
  id BIGSERIAL PRIMARY KEY,
  type VARCHAR(100) NOT NULL,
  recipient_user_id BIGINT NOT NULL REFERENCES users(id),
  title VARCHAR(255) NOT NULL,
  message TEXT NOT NULL,
  entity_type VARCHAR(100) NOT NULL,
  entity_id VARCHAR(255) NOT NULL,
  dedup_key VARCHAR(255) NOT NULL,
  is_read BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_system_notification_dedup UNIQUE (dedup_key)
);
