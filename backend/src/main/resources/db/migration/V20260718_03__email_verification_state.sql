ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verification_requested_at TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified_at TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_delivery_status VARCHAR(16);

-- Existing raw tokens cannot safely be migrated to hashes without exposing them again.
-- Invalidate legacy outstanding tokens; users can request a fresh one through resend.
UPDATE users
SET email_verification_token = NULL,
    email_verification_token_expiry = NULL
WHERE email_verified = FALSE AND email_verification_token IS NOT NULL
  AND length(email_verification_token) <> 64;

CREATE INDEX IF NOT EXISTS idx_users_verification_token_hash
    ON users(email_verification_token)
    WHERE email_verification_token IS NOT NULL;
