CREATE TABLE IF NOT EXISTS revenue_settings (
    id BIGINT PRIMARY KEY,
    commission_rate NUMERIC(5,4) NOT NULL DEFAULT 0.2000,
    updated_at TIMESTAMP,
    updated_by BIGINT REFERENCES users(id)
);

INSERT INTO revenue_settings (id, commission_rate)
VALUES (1, 0.2000)
ON CONFLICT (id) DO NOTHING;

ALTER TABLE legal_tickets
ADD COLUMN IF NOT EXISTS commission_rate NUMERIC(5,4),
ADD COLUMN IF NOT EXISTS platform_fee NUMERIC(19,2),
ADD COLUMN IF NOT EXISTS expert_payout NUMERIC(19,2);
