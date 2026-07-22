ALTER TABLE legal_tickets
    ADD COLUMN IF NOT EXISTS creation_source VARCHAR(32),
    ADD COLUMN IF NOT EXISTS legal_issue_category VARCHAR(255),
    ADD COLUMN IF NOT EXISTS contract_type VARCHAR(255),
    ADD COLUMN IF NOT EXISTS user_expected_outcome TEXT,
    ADD COLUMN IF NOT EXISTS shared_profile_fields_json TEXT,
    ADD COLUMN IF NOT EXISTS consent_granted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS consent_revoked_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS proposed_expert_id BIGINT REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS ticket_complexity VARCHAR(32),
    ADD COLUMN IF NOT EXISTS classification_reason TEXT,
    ADD COLUMN IF NOT EXISTS classified_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS classified_by_id BIGINT REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS pricing_type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS user_price NUMERIC(19,2),
    ADD COLUMN IF NOT EXISTS internal_ticket_value NUMERIC(19,2),
    ADD COLUMN IF NOT EXISTS quote_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS customer_payment_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS customer_payment_reference VARCHAR(255),
    ADD COLUMN IF NOT EXISTS quota_cycle VARCHAR(64),
    ADD COLUMN IF NOT EXISTS quota_reservation_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS assignment_offered_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS acceptance_due_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS accepted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS started_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS first_response_due_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS first_responded_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS resolution_due_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_expert_activity_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS sla_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS paused_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS total_paused_duration_seconds BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS extension_reason TEXT,
    ADD COLUMN IF NOT EXISTS failure_responsible_party VARCHAR(32),
    ADD COLUMN IF NOT EXISTS previous_expert_id BIGINT REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS reassignment_reason TEXT,
    ADD COLUMN IF NOT EXISTS reassigned_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS reassigned_by_id BIGINT REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS completion_percent INTEGER,
    ADD COLUMN IF NOT EXISTS approved_partial_payout NUMERIC(19,2),
    ADD COLUMN IF NOT EXISTS contribution_note TEXT;

UPDATE legal_tickets
SET creation_source = CASE
    WHEN related_chat_session_id IS NOT NULL OR source_assistant_message_id IS NOT NULL THEN 'AI_CHAT'
    ELSE 'MANUAL_FORM'
END
WHERE creation_source IS NULL;

ALTER TABLE legal_tickets ALTER COLUMN creation_source SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_legal_tickets_proposed_expert_status
    ON legal_tickets(proposed_expert_id, status) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_legal_tickets_acceptance_due
    ON legal_tickets(acceptance_due_at) WHERE status = 'PENDING_EXPERT_ACCEPTANCE';
CREATE INDEX IF NOT EXISTS idx_legal_tickets_sla_due
    ON legal_tickets(resolution_due_at, sla_status) WHERE deleted = FALSE;

CREATE TABLE IF NOT EXISTS expert_ticket_credit_reservations (
    id VARCHAR(255) PRIMARY KEY,
    ticket_id VARCHAR(255) NOT NULL,
    user_id BIGINT NOT NULL,
    quota_cycle VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    reserved_at TIMESTAMP,
    consumed_at TIMESTAMP,
    released_at TIMESTAMP,
    release_reason VARCHAR(255),
    version BIGINT,
    CONSTRAINT uk_expert_ticket_credit_ticket UNIQUE (ticket_id),
    CONSTRAINT fk_expert_ticket_credit_ticket FOREIGN KEY (ticket_id) REFERENCES legal_tickets(id),
    CONSTRAINT fk_expert_ticket_credit_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_expert_ticket_credit_user_cycle
    ON expert_ticket_credit_reservations(user_id, quota_cycle, status);

ALTER TABLE ticket_audit_logs
    ADD COLUMN IF NOT EXISTS actor_type VARCHAR(32) NOT NULL DEFAULT 'USER',
    ALTER COLUMN actor_id DROP NOT NULL;
