-- The expert-ticket workflow introduced states after the original legal_tickets
-- table was created. Keep the database constraint aligned with LegalTicketStatus
-- instead of disabling status validation.
ALTER TABLE legal_tickets
    DROP CONSTRAINT IF EXISTS legal_tickets_status_check;

ALTER TABLE legal_tickets
    ADD CONSTRAINT legal_tickets_status_check CHECK (status IN (
        'OPEN',
        'ASSIGNED',
        'WAITING_FOR_USER',
        'WAITING_FOR_EXPERT',
        'DRAFT',
        'PENDING_ADMIN_REVIEW',
        'PENDING_EXPERT_ASSESSMENT',
        'RECLASSIFICATION_REQUESTED',
        'WAITING_USER_ACCEPTANCE',
        'WAITING_PAYMENT',
        'READY_FOR_ASSIGNMENT',
        'PENDING_EXPERT_ACCEPTANCE',
        'ASSIGNED_TO_EXPERT',
        'REJECTED_BY_ADMIN',
        'ASSIGNED_TO_LAWYER',
        'IN_REVIEW',
        'NEED_MORE_INFO',
        'CUSTOMER_RESPONDED',
        'INACTIVE_WARNING',
        'OVERDUE',
        'SLA_BREACHED',
        'ADMIN_INTERVENTION',
        'PENDING_REASSIGNMENT',
        'PARTIALLY_COMPLETED',
        'RESOLVED',
        'CLOSED',
        'CANCELLED',
        'CANCELLED_BY_USER',
        'REOPENED',
        'FAILED_BY_EXPERT',
        'FAILED_BY_USER',
        'REFUND_PENDING',
        'REFUNDED'
    ));

-- This workflow field exists in the entity and is used when an expert is
-- offered an assignment. IF NOT EXISTS keeps the migration safe for databases
-- where Hibernate previously created the column.
ALTER TABLE legal_tickets
    ADD COLUMN IF NOT EXISTS acceptance_due_at timestamp(6),
    ADD COLUMN IF NOT EXISTS approved_partial_payout numeric(19,2),
    ADD COLUMN IF NOT EXISTS assignment_offered_at timestamp(6),
    ADD COLUMN IF NOT EXISTS classification_reason TEXT,
    ADD COLUMN IF NOT EXISTS classified_at timestamp(6),
    ADD COLUMN IF NOT EXISTS classified_by_id bigint,
    ADD COLUMN IF NOT EXISTS completion_percent integer,
    ADD COLUMN IF NOT EXISTS consent_granted_at timestamp(6),
    ADD COLUMN IF NOT EXISTS consent_revoked_at timestamp(6),
    ADD COLUMN IF NOT EXISTS contribution_note TEXT,
    ADD COLUMN IF NOT EXISTS creation_source varchar(255),
    ADD COLUMN IF NOT EXISTS customer_payment_reference varchar(255),
    ADD COLUMN IF NOT EXISTS customer_payment_status varchar(255),
    ADD COLUMN IF NOT EXISTS extension_reason TEXT,
    ADD COLUMN IF NOT EXISTS failure_responsible_party varchar(255),
    ADD COLUMN IF NOT EXISTS first_responded_at timestamp(6),
    ADD COLUMN IF NOT EXISTS first_response_due_at timestamp(6),
    ADD COLUMN IF NOT EXISTS internal_ticket_value numeric(19,2),
    ADD COLUMN IF NOT EXISTS last_expert_activity_at timestamp(6),
    ADD COLUMN IF NOT EXISTS legal_issue_category varchar(255),
    ADD COLUMN IF NOT EXISTS paused_at timestamp(6),
    ADD COLUMN IF NOT EXISTS previous_expert_id bigint,
    ADD COLUMN IF NOT EXISTS pricing_type varchar(255),
    ADD COLUMN IF NOT EXISTS proposed_expert_id bigint,
    ADD COLUMN IF NOT EXISTS quota_cycle varchar(255),
    ADD COLUMN IF NOT EXISTS quota_reservation_status varchar(255),
    ADD COLUMN IF NOT EXISTS quote_status varchar(255),
    ADD COLUMN IF NOT EXISTS reassigned_at timestamp(6),
    ADD COLUMN IF NOT EXISTS reassigned_by_id bigint,
    ADD COLUMN IF NOT EXISTS reassignment_reason TEXT,
    ADD COLUMN IF NOT EXISTS resolution_due_at timestamp(6),
    ADD COLUMN IF NOT EXISTS shared_profile_fields_json TEXT,
    ADD COLUMN IF NOT EXISTS sla_status varchar(255),
    ADD COLUMN IF NOT EXISTS ticket_complexity varchar(255),
    ADD COLUMN IF NOT EXISTS total_paused_duration_seconds bigint,
    ADD COLUMN IF NOT EXISTS user_expected_outcome TEXT,
    ADD COLUMN IF NOT EXISTS user_price numeric(19,2);

UPDATE legal_tickets
SET creation_source = 'AI_CHAT'
WHERE creation_source IS NULL;

ALTER TABLE legal_tickets
    ALTER COLUMN creation_source SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'legal_tickets'::regclass
          AND conname = 'fk_legal_tickets_classified_by'
    ) THEN
        ALTER TABLE legal_tickets
            ADD CONSTRAINT fk_legal_tickets_classified_by
            FOREIGN KEY (classified_by_id) REFERENCES users(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'legal_tickets'::regclass
          AND conname = 'fk_legal_tickets_proposed_expert'
    ) THEN
        ALTER TABLE legal_tickets
            ADD CONSTRAINT fk_legal_tickets_proposed_expert
            FOREIGN KEY (proposed_expert_id) REFERENCES users(id);
    END IF;
END $$;

ALTER TABLE ticket_audit_logs
    ADD COLUMN IF NOT EXISTS actor_type varchar(255);

UPDATE ticket_audit_logs
SET actor_type = CASE WHEN actor_id IS NULL THEN 'SYSTEM' ELSE 'USER' END
WHERE actor_type IS NULL;

ALTER TABLE ticket_audit_logs
    ALTER COLUMN actor_type SET NOT NULL,
    ALTER COLUMN actor_id DROP NOT NULL;

CREATE TABLE IF NOT EXISTS expert_ticket_credit_reservations (
    id varchar(255) PRIMARY KEY,
    ticket_id varchar(255) NOT NULL,
    user_id bigint NOT NULL,
    quota_cycle varchar(64) NOT NULL,
    status varchar(32) NOT NULL,
    reserved_at timestamp(6),
    consumed_at timestamp(6),
    released_at timestamp(6),
    release_reason varchar(255),
    version bigint,
    CONSTRAINT uk_expert_ticket_credit_ticket UNIQUE (ticket_id),
    CONSTRAINT fk_expert_ticket_credit_ticket
        FOREIGN KEY (ticket_id) REFERENCES legal_tickets(id),
    CONSTRAINT fk_expert_ticket_credit_user
        FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT expert_ticket_credit_status_check CHECK (status IN (
        'RESERVED', 'CONSUMED', 'RELEASED'
    ))
);

CREATE INDEX IF NOT EXISTS idx_expert_ticket_credit_user_cycle
    ON expert_ticket_credit_reservations (user_id, quota_cycle, status);
