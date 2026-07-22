ALTER TABLE payment_transactions
    ALTER COLUMN subscription_plan_id DROP NOT NULL;

ALTER TABLE payment_transactions
    ADD COLUMN IF NOT EXISTS legal_ticket_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS payment_purpose VARCHAR(32) NOT NULL DEFAULT 'SUBSCRIPTION';

UPDATE payment_transactions
SET payment_purpose = 'SUBSCRIPTION'
WHERE payment_purpose IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_payment_transactions_legal_ticket'
    ) THEN
        ALTER TABLE payment_transactions
            ADD CONSTRAINT fk_payment_transactions_legal_ticket
            FOREIGN KEY (legal_ticket_id) REFERENCES legal_tickets(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_payment_transactions_legal_ticket
    ON payment_transactions (legal_ticket_id, payment_status);
