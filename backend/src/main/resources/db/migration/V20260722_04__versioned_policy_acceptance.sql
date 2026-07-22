CREATE TABLE IF NOT EXISTS policy_acceptances (
    id VARCHAR(40) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    terms_version VARCHAR(40) NOT NULL,
    privacy_policy_version VARCHAR(40) NOT NULL,
    accepted_at TIMESTAMP NOT NULL,
    ip_hash VARCHAR(64),
    user_agent_hash VARCHAR(64),
    CONSTRAINT uk_policy_acceptance_user_versions UNIQUE (user_id, terms_version, privacy_policy_version)
);
CREATE INDEX IF NOT EXISTS idx_policy_acceptance_user ON policy_acceptances(user_id);
