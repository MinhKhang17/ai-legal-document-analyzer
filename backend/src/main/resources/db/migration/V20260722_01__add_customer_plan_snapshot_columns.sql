ALTER TABLE customer_plans ADD COLUMN IF NOT EXISTS plan_name_snapshot varchar(255);
ALTER TABLE customer_plans ADD COLUMN IF NOT EXISTS plan_type_snapshot varchar(255);
ALTER TABLE customer_plans ADD COLUMN IF NOT EXISTS price_snapshot numeric(38,2);
ALTER TABLE customer_plans ADD COLUMN IF NOT EXISTS billing_cycle_days_snapshot integer;
ALTER TABLE customer_plans ADD COLUMN IF NOT EXISTS analysis_limit_snapshot integer;
ALTER TABLE customer_plans ADD COLUMN IF NOT EXISTS ai_token_limit_snapshot integer;
ALTER TABLE customer_plans ADD COLUMN IF NOT EXISTS workspace_limit_snapshot integer;
ALTER TABLE customer_plans ADD COLUMN IF NOT EXISTS documents_per_workspace_limit_snapshot integer;
ALTER TABLE customer_plans ADD COLUMN IF NOT EXISTS storage_limit_mb_snapshot integer;
ALTER TABLE customer_plans ADD COLUMN IF NOT EXISTS max_file_size_mb_snapshot integer;
ALTER TABLE customer_plans ADD COLUMN IF NOT EXISTS attached_documents_limit_snapshot integer;
ALTER TABLE customer_plans ADD COLUMN IF NOT EXISTS draft_contract_limit_snapshot integer;
ALTER TABLE customer_plans ADD COLUMN IF NOT EXISTS expert_ticket_limit_snapshot integer;
ALTER TABLE customer_plans ADD COLUMN IF NOT EXISTS allow_contact_expert_ticket_snapshot boolean;

UPDATE customer_plans cp
SET plan_name_snapshot = sp.plan_name,
    plan_type_snapshot = sp.plan_type,
    price_snapshot = sp.price,
    billing_cycle_days_snapshot = sp.duration_days,
    analysis_limit_snapshot = sp.max_quota,
    ai_token_limit_snapshot = sp.ai_quota,
    workspace_limit_snapshot = sp.max_workspaces,
    documents_per_workspace_limit_snapshot = sp.max_contracts_per_workspace,
    storage_limit_mb_snapshot = sp.storage_limit_mb,
    max_file_size_mb_snapshot = sp.max_file_size_mb,
    attached_documents_limit_snapshot = sp.max_attached_documents_per_session,
    draft_contract_limit_snapshot = sp.max_draft_contracts,
    expert_ticket_limit_snapshot = sp.ticket_quota,
    allow_contact_expert_ticket_snapshot = sp.allow_contact_expert_ticket
FROM subscription_plans sp
WHERE cp.subscription_plan_id = sp.id
  AND cp.status IN ('ACTIVE', 'PENDING')
  AND cp.plan_name_snapshot IS NULL;
