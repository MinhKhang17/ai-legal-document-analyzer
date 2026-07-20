ALTER TABLE documents ADD COLUMN IF NOT EXISTS contract_type VARCHAR(64);
ALTER TABLE documents ADD COLUMN IF NOT EXISTS contract_type_confirmed BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_documents_contract_type
    ON documents(contract_type, contract_type_confirmed);

UPDATE user_contracts SET contract_type = 'RENTAL'
WHERE UPPER(contract_type) IN ('LEASE', 'STUDENT_RENTAL');
UPDATE user_contracts SET contract_type = 'PART_TIME_EMPLOYMENT'
WHERE UPPER(contract_type) IN ('PART_TIME', 'PART_TIME_OR_INTERNSHIP');
UPDATE user_contracts SET contract_type = 'FREELANCE_SERVICE'
WHERE UPPER(contract_type) IN ('SERVICE', 'SMALL_SERVICE_OR_FREELANCE');
UPDATE user_contracts SET contract_type = 'PERSONAL_LOAN'
WHERE UPPER(contract_type) IN ('LOAN', 'PERSONAL_LOAN_SIMPLE');

UPDATE contract_templates SET category = 'RENTAL'
WHERE UPPER(category) IN ('LEASE', 'STUDENT_RENTAL');
UPDATE contract_templates SET category = 'PART_TIME_EMPLOYMENT'
WHERE UPPER(category) IN ('PART_TIME', 'PART_TIME_OR_INTERNSHIP');
UPDATE contract_templates SET category = 'FREELANCE_SERVICE'
WHERE UPPER(category) IN ('SERVICE', 'SMALL_SERVICE_OR_FREELANCE');
UPDATE contract_templates SET category = 'PERSONAL_LOAN'
WHERE UPPER(category) IN ('LOAN', 'PERSONAL_LOAN_SIMPLE');
