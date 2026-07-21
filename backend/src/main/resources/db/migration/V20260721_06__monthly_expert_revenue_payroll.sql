CREATE TABLE IF NOT EXISTS revenue_periods (
    id VARCHAR(255) PRIMARY KEY, period_code VARCHAR(20) NOT NULL,
    start_date DATE NOT NULL, end_date DATE NOT NULL, cutoff_at TIMESTAMP NOT NULL,
    status VARCHAR(24) NOT NULL, closed_at TIMESTAMP, closed_by_id BIGINT REFERENCES users(id),
    total_gross NUMERIC(19,2) NOT NULL DEFAULT 0, total_platform_fee NUMERIC(19,2) NOT NULL DEFAULT 0,
    total_expert_payout NUMERIC(19,2) NOT NULL DEFAULT 0, total_adjustments NUMERIC(19,2) NOT NULL DEFAULT 0,
    total_final_payout NUMERIC(19,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0, CONSTRAINT uk_revenue_period_code UNIQUE(period_code),
    CONSTRAINT ck_revenue_period_dates CHECK (start_date <= end_date)
);

CREATE TABLE IF NOT EXISTS expert_revenue_statements (
    id VARCHAR(255) PRIMARY KEY, period_id VARCHAR(255) NOT NULL REFERENCES revenue_periods(id),
    expert_id BIGINT NOT NULL REFERENCES users(id), expert_name_snapshot VARCHAR(255) NOT NULL,
    ticket_count BIGINT NOT NULL DEFAULT 0, gross_consultation_fee NUMERIC(19,2) NOT NULL DEFAULT 0,
    total_platform_fee NUMERIC(19,2) NOT NULL DEFAULT 0, total_expert_payout NUMERIC(19,2) NOT NULL DEFAULT 0,
    adjustment_amount NUMERIC(19,2) NOT NULL DEFAULT 0, final_payout NUMERIC(19,2) NOT NULL DEFAULT 0,
    paid_amount NUMERIC(19,2) NOT NULL DEFAULT 0, remaining_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL, generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMP, paid_at TIMESTAMP, payment_reference VARCHAR(255), version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_statement_period_expert UNIQUE(period_id, expert_id),
    CONSTRAINT ck_statement_amounts CHECK (paid_amount >= 0)
);

CREATE TABLE IF NOT EXISTS expert_revenue_statement_items (
    id VARCHAR(255) PRIMARY KEY, statement_id VARCHAR(255) NOT NULL REFERENCES expert_revenue_statements(id),
    ticket_id VARCHAR(255) NOT NULL REFERENCES legal_tickets(id), ticket_code VARCHAR(255) NOT NULL,
    consultation_fee NUMERIC(19,2) NOT NULL, commission_rate_snapshot NUMERIC(7,6) NOT NULL,
    platform_fee NUMERIC(19,2) NOT NULL, expert_payout NUMERIC(19,2) NOT NULL,
    recognized_at TIMESTAMP NOT NULL, assigned_expert_id_snapshot BIGINT NOT NULL,
    ticket_status_snapshot VARCHAR(40) NOT NULL,
    CONSTRAINT uk_statement_item_ticket UNIQUE(ticket_id),
    CONSTRAINT ck_statement_item_rate CHECK (commission_rate_snapshot >= 0 AND commission_rate_snapshot <= 1)
);

CREATE TABLE IF NOT EXISTS revenue_adjustments (
    id VARCHAR(255) PRIMARY KEY, original_period_id VARCHAR(255) REFERENCES revenue_periods(id),
    applied_period_id VARCHAR(255) NOT NULL REFERENCES revenue_periods(id), expert_id BIGINT NOT NULL REFERENCES users(id),
    ticket_id VARCHAR(255) REFERENCES legal_tickets(id), type VARCHAR(32) NOT NULL,
    amount NUMERIC(19,2) NOT NULL, reason TEXT NOT NULL, created_by_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS commission_policy_change_requests (
    id VARCHAR(255) PRIMARY KEY, old_rate_snapshot NUMERIC(7,6) NOT NULL, new_rate NUMERIC(7,6) NOT NULL,
    application_type VARCHAR(20) NOT NULL, effective_from DATE NOT NULL, reason TEXT NOT NULL,
    status VARCHAR(40) NOT NULL, requested_by_id BIGINT NOT NULL REFERENCES users(id),
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, token_hash VARCHAR(255) UNIQUE,
    token_expires_at TIMESTAMP, verified_at TIMESTAMP, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_commission_request_rate CHECK (new_rate >= 0 AND new_rate <= 1)
);

CREATE TABLE IF NOT EXISTS commission_policies (
    id VARCHAR(255) PRIMARY KEY, rate NUMERIC(7,6) NOT NULL, effective_from DATE NOT NULL, effective_to DATE,
    status VARCHAR(20) NOT NULL, reason TEXT, source_change_request_id VARCHAR(255) REFERENCES commission_policy_change_requests(id),
    created_by_id BIGINT REFERENCES users(id), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activated_at TIMESTAMP, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_policy_source_request UNIQUE(source_change_request_id),
    CONSTRAINT ck_commission_policy_rate CHECK (rate >= 0 AND rate <= 1),
    CONSTRAINT ck_commission_policy_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE TABLE IF NOT EXISTS commission_policy_expert_notifications (
    id BIGSERIAL PRIMARY KEY, policy_id VARCHAR(255) NOT NULL REFERENCES commission_policies(id),
    expert_id BIGINT NOT NULL REFERENCES users(id), expert_email_snapshot VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL, retry_count INTEGER NOT NULL DEFAULT 0,
    sent_at TIMESTAMP, failed_at TIMESTAMP, read_at TIMESTAMP,
    CONSTRAINT uk_policy_expert_notification UNIQUE(policy_id, expert_id)
);

CREATE TABLE IF NOT EXISTS early_payout_requests (
    id VARCHAR(255) PRIMARY KEY, request_code VARCHAR(255) NOT NULL UNIQUE,
    expert_id BIGINT NOT NULL REFERENCES users(id), period_id VARCHAR(255) NOT NULL REFERENCES revenue_periods(id),
    statement_id VARCHAR(255) NOT NULL REFERENCES expert_revenue_statements(id),
    requested_amount NUMERIC(19,2) NOT NULL, eligible_amount_snapshot NUMERIC(19,2) NOT NULL,
    approved_amount NUMERIC(19,2), reason TEXT NOT NULL, expert_note TEXT, admin_note TEXT,
    status VARCHAR(32) NOT NULL, requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP, reviewed_by_id BIGINT REFERENCES users(id), approved_at TIMESTAMP,
    rejected_at TIMESTAMP, paid_at TIMESTAMP, payment_reference VARCHAR(255), idempotency_key VARCHAR(255) UNIQUE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_early_requested_amount CHECK (requested_amount > 0),
    CONSTRAINT ck_early_approved_amount CHECK (approved_amount IS NULL OR approved_amount > 0)
);

CREATE TABLE IF NOT EXISTS expert_payout_transactions (
    id VARCHAR(255) PRIMARY KEY, expert_id BIGINT NOT NULL REFERENCES users(id),
    statement_id VARCHAR(255) NOT NULL REFERENCES expert_revenue_statements(id),
    early_payout_request_id VARCHAR(255) UNIQUE REFERENCES early_payout_requests(id),
    amount NUMERIC(19,2) NOT NULL, type VARCHAR(16) NOT NULL, status VARCHAR(16) NOT NULL,
    paid_at TIMESTAMP, payment_reference VARCHAR(255), paid_by_id BIGINT REFERENCES users(id),
    idempotency_key VARCHAR(255) UNIQUE, CONSTRAINT ck_payout_amount CHECK (amount > 0)
);

CREATE TABLE IF NOT EXISTS financial_audit_logs (
    id VARCHAR(255) PRIMARY KEY, action VARCHAR(255) NOT NULL, actor_id BIGINT REFERENCES users(id),
    entity_type VARCHAR(255) NOT NULL, entity_id VARCHAR(255) NOT NULL,
    old_values_json TEXT, new_values_json TEXT, reason TEXT, request_id VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_revenue_period_dates ON revenue_periods(start_date, end_date);
CREATE INDEX IF NOT EXISTS idx_statement_expert_period ON expert_revenue_statements(expert_id, period_id);
CREATE INDEX IF NOT EXISTS idx_statement_item_statement ON expert_revenue_statement_items(statement_id);
CREATE INDEX IF NOT EXISTS idx_adjustment_applied_expert ON revenue_adjustments(applied_period_id, expert_id);
CREATE INDEX IF NOT EXISTS idx_policy_effective_status ON commission_policies(effective_from, effective_to, status);
CREATE UNIQUE INDEX IF NOT EXISTS uk_policy_effective_live ON commission_policies(effective_from) WHERE status IN ('SCHEDULED','ACTIVE');
CREATE INDEX IF NOT EXISTS idx_policy_request_token ON commission_policy_change_requests(token_hash);
CREATE INDEX IF NOT EXISTS idx_early_payout_admin_queue ON early_payout_requests(status, requested_at);
CREATE UNIQUE INDEX IF NOT EXISTS uk_early_payout_open_per_period ON early_payout_requests(expert_id, period_id)
    WHERE status IN ('PENDING_ADMIN_REVIEW','NEED_MORE_INFO','EXPERT_RESPONDED','APPROVED','PAYMENT_PENDING');
CREATE INDEX IF NOT EXISTS idx_financial_audit_created ON financial_audit_logs(created_at);

INSERT INTO commission_policies(id, rate, effective_from, status, reason, created_at, activated_at, version)
SELECT 'policy_initial', COALESCE((SELECT commission_rate FROM revenue_settings WHERE id=1), 0.2000), DATE '1970-01-01',
       'ACTIVE', 'Initial policy migrated from revenue settings', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM commission_policies);

INSERT INTO revenue_periods(id, period_code, start_date, end_date, cutoff_at, status, closed_at,
                            total_gross, total_platform_fee, total_expert_payout, total_adjustments, total_final_payout,
                            created_at, updated_at, version)
SELECT 'revperiod_historical', 'HISTORICAL', DATE '1970-01-01', CURRENT_DATE - 1,
       CURRENT_DATE::timestamp, 'CLOSED', CURRENT_TIMESTAMP,
       COALESCE(SUM(COALESCE(consultation_fee,0)),0), COALESCE(SUM(COALESCE(platform_fee,0)),0),
       COALESCE(SUM(COALESCE(expert_payout,0)),0), 0, COALESCE(SUM(COALESCE(expert_payout,0)),0),
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM legal_tickets
WHERE assigned_lawyer_id IS NOT NULL AND status IN ('RESOLVED','CLOSED')
HAVING COUNT(*) > 0
ON CONFLICT (period_code) DO NOTHING;

INSERT INTO expert_revenue_statements(id, period_id, expert_id, expert_name_snapshot, ticket_count,
    gross_consultation_fee, total_platform_fee, total_expert_payout, adjustment_amount, final_payout,
    paid_amount, remaining_amount, status, generated_at, confirmed_at, version)
SELECT 'stmt_hist_' || md5(t.assigned_lawyer_id::text), 'revperiod_historical', t.assigned_lawyer_id,
       trim(u.first_name || ' ' || u.last_name), COUNT(*), SUM(COALESCE(t.consultation_fee,0)),
       SUM(COALESCE(t.platform_fee,0)), SUM(COALESCE(t.expert_payout,0)), 0,
       SUM(COALESCE(t.expert_payout,0)),
       SUM(CASE WHEN t.expert_payment_status='PAID' THEN COALESCE(t.expert_payout,0) ELSE 0 END),
       SUM(COALESCE(t.expert_payout,0))-SUM(CASE WHEN t.expert_payment_status='PAID' THEN COALESCE(t.expert_payout,0) ELSE 0 END),
       CASE WHEN bool_and(t.expert_payment_status='PAID') THEN 'PAID' ELSE 'CONFIRMED' END,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM legal_tickets t JOIN users u ON u.id=t.assigned_lawyer_id
WHERE t.assigned_lawyer_id IS NOT NULL AND t.status IN ('RESOLVED','CLOSED')
  AND EXISTS (SELECT 1 FROM revenue_periods WHERE id='revperiod_historical')
GROUP BY t.assigned_lawyer_id,u.first_name,u.last_name
ON CONFLICT (period_id,expert_id) DO NOTHING;

INSERT INTO expert_revenue_statement_items(id, statement_id, ticket_id, ticket_code, consultation_fee,
    commission_rate_snapshot, platform_fee, expert_payout, recognized_at, assigned_expert_id_snapshot, ticket_status_snapshot)
SELECT 'stmtitem_hist_' || md5(t.id), 'stmt_hist_' || md5(t.assigned_lawyer_id::text), t.id, COALESCE(t.ticket_code,t.id),
       COALESCE(t.consultation_fee,0), COALESCE(t.commission_rate,(SELECT rate FROM commission_policies WHERE id='policy_initial')),
       COALESCE(t.platform_fee,ROUND(COALESCE(t.consultation_fee,0)*COALESCE(t.commission_rate,(SELECT rate FROM commission_policies WHERE id='policy_initial')),2)),
       COALESCE(t.expert_payout,COALESCE(t.consultation_fee,0)-ROUND(COALESCE(t.consultation_fee,0)*COALESCE(t.commission_rate,(SELECT rate FROM commission_policies WHERE id='policy_initial')),2)),
       COALESCE(t.resolved_at,t.updated_at,t.created_at), t.assigned_lawyer_id, t.status
FROM legal_tickets t
WHERE t.assigned_lawyer_id IS NOT NULL AND t.status IN ('RESOLVED','CLOSED')
  AND EXISTS (SELECT 1 FROM expert_revenue_statements s WHERE s.id='stmt_hist_' || md5(t.assigned_lawyer_id::text))
ON CONFLICT (ticket_id) DO NOTHING;
