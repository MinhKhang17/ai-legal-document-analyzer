-- These workflow/entity fields exist on LegalTicket but were never added to
-- the legal_tickets table by earlier reconciliation migrations, causing
-- Hibernate schema validation to fail with "missing column".
ALTER TABLE legal_tickets
    ADD COLUMN IF NOT EXISTS contract_type varchar(255),
    ADD COLUMN IF NOT EXISTS started_at timestamp(6),
    ADD COLUMN IF NOT EXISTS accepted_at timestamp(6),
    ADD COLUMN IF NOT EXISTS completed_at timestamp(6);
