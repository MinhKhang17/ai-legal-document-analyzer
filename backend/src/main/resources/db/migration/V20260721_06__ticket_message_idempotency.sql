ALTER TABLE legal_ticket_messages
    ADD COLUMN IF NOT EXISTS client_message_id VARCHAR(100);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ticket_message_client_id
    ON legal_ticket_messages(ticket_id, sender_id, client_message_id)
    WHERE client_message_id IS NOT NULL;
