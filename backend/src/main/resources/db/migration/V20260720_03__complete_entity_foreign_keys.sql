-- Add missing entity foreign keys without duplicating differently named legacy constraints.
DO $$
DECLARE
    fk RECORD;
BEGIN
    FOR fk IN
        SELECT * FROM (VALUES
        ('ai_citations', 'chat_message_id', 'chat_messages', 'id', 'fk_ai_citations_chat_message_id'),
        ('ai_citations', 'legal_ticket_id', 'legal_tickets', 'id', 'fk_ai_citations_legal_ticket_id'),
        ('ai_reports', 'submitted_by_id', 'users', 'id', 'fk_ai_reports_submitted_by_id'),
        ('ai_reports', 'workspace_id', 'workspaces', 'id', 'fk_ai_reports_workspace_id'),
        ('chat_message_feedbacks', 'chat_message_id', 'chat_messages', 'id', 'fk_chat_message_feedbacks_chat_message_id'),
        ('chat_message_feedbacks', 'chat_session_id', 'chat_sessions', 'id', 'fk_chat_message_feedbacks_chat_session_id'),
        ('chat_message_feedbacks', 'user_id', 'users', 'id', 'fk_chat_message_feedbacks_user_id'),
        ('chat_messages', 'chat_session_id', 'chat_sessions', 'id', 'fk_chat_messages_chat_session_id'),
        ('chat_messages', 'user_id', 'users', 'id', 'fk_chat_messages_user_id'),
        ('chat_session_documents', 'chat_session_id', 'chat_sessions', 'id', 'fk_chat_session_documents_chat_session_id'),
        ('chat_session_documents', 'document_id', 'documents', 'id', 'fk_chat_session_documents_document_id'),
        ('chat_session_documents', 'user_id', 'users', 'id', 'fk_chat_session_documents_user_id'),
        ('chat_sessions', 'user_id', 'users', 'id', 'fk_chat_sessions_user_id'),
        ('chat_sessions', 'workspace_id', 'workspaces', 'id', 'fk_chat_sessions_workspace_id'),
        ('contract_generation_jobs', 'requester_id', 'users', 'id', 'fk_contract_generation_jobs_requester_id'),
        ('contract_generation_jobs', 'source_document_id', 'documents', 'id', 'fk_contract_generation_jobs_source_document_id'),
        ('contract_generation_jobs', 'template_id', 'contract_templates', 'id', 'fk_contract_generation_jobs_template_id'),
        ('contract_generation_jobs', 'workspace_id', 'workspaces', 'id', 'fk_contract_generation_jobs_workspace_id'),
        ('contract_versions', 'contract_id', 'user_contracts', 'id', 'fk_contract_versions_contract_id'),
        ('contract_versions', 'generated_by_id', 'users', 'id', 'fk_contract_versions_generated_by_id'),
        ('contract_versions', 'generation_job_id', 'contract_generation_jobs', 'id', 'fk_contract_versions_generation_job_id'),
        ('conversation_shares', 'created_by_id', 'users', 'id', 'fk_conversation_shares_created_by_id'),
        ('conversation_shares', 'ticket_id', 'legal_tickets', 'id', 'fk_conversation_shares_ticket_id'),
        ('customer_plans', 'customer_id', 'users', 'id', 'fk_customer_plans_customer_id'),
        ('customer_plans', 'scheduled_subscription_plan_id', 'subscription_plans', 'id', 'fk_customer_plans_scheduled_subscription_plan_id'),
        ('customer_plans', 'subscription_plan_id', 'subscription_plans', 'id', 'fk_customer_plans_subscription_plan_id'),
        ('documents', 'legal_ticket_id', 'legal_tickets', 'id', 'fk_documents_legal_ticket_id'),
        ('documents', 'user_id', 'users', 'id', 'fk_documents_user_id'),
        ('documents', 'workspace_id', 'workspaces', 'id', 'fk_documents_workspace_id'),
        ('feedback_survey_responses', 'respondent_id', 'users', 'id', 'fk_feedback_survey_responses_respondent_id'),
        ('feedback_survey_responses', 'survey_id', 'feedback_surveys', 'id', 'fk_feedback_survey_responses_survey_id'),
        ('feedback_surveys', 'created_by_id', 'users', 'id', 'fk_feedback_surveys_created_by_id'),
        ('feedback_surveys', 'workspace_id', 'workspaces', 'id', 'fk_feedback_surveys_workspace_id'),
        ('knowledge_base_entries', 'created_by_id', 'users', 'id', 'fk_knowledge_base_entries_created_by_id'),
        ('knowledge_base_entries', 'workspace_id', 'workspaces', 'id', 'fk_knowledge_base_entries_workspace_id'),
        ('knowledge_base_versions', 'archived_by_id', 'users', 'id', 'fk_knowledge_base_versions_archived_by_id'),
        ('knowledge_base_versions', 'ingested_by_id', 'users', 'id', 'fk_knowledge_base_versions_ingested_by_id'),
        ('knowledge_base_versions', 'knowledge_base_entry_id', 'knowledge_base_entries', 'id', 'fk_knowledge_base_versions_knowledge_base_entry_id'),
        ('knowledge_base_versions', 'published_by_id', 'users', 'id', 'fk_knowledge_base_versions_published_by_id'),
        ('knowledge_base_versions', 'reviewed_by_id', 'users', 'id', 'fk_knowledge_base_versions_reviewed_by_id'),
        ('knowledge_base_versions', 'source_document_id', 'documents', 'id', 'fk_knowledge_base_versions_source_document_id'),
        ('knowledge_ingestion_jobs', 'ingested_by_id', 'users', 'id', 'fk_knowledge_ingestion_jobs_ingested_by_id'),
        ('knowledge_ingestion_jobs', 'knowledge_base_version_id', 'knowledge_base_versions', 'id', 'fk_knowledge_ingestion_jobs_knowledge_base_version_id'),
        ('legal_ticket_messages', 'reply_to_message_id', 'legal_ticket_messages', 'id', 'fk_legal_ticket_messages_reply_to_message_id'),
        ('legal_ticket_messages', 'sender_id', 'users', 'id', 'fk_legal_ticket_messages_sender_id'),
        ('legal_ticket_messages', 'ticket_id', 'legal_tickets', 'id', 'fk_legal_ticket_messages_ticket_id'),
        ('legal_tickets', 'assigned_lawyer_id', 'users', 'id', 'fk_legal_tickets_assigned_lawyer_id'),
        ('legal_tickets', 'created_by_id', 'users', 'id', 'fk_legal_tickets_created_by_id'),
        ('legal_tickets', 'document_id', 'documents', 'id', 'fk_legal_tickets_document_id'),
        ('legal_tickets', 'workspace_id', 'workspaces', 'id', 'fk_legal_tickets_workspace_id'),
        ('payment_transactions', 'customer_id', 'users', 'id', 'fk_payment_transactions_customer_id'),
        ('payment_transactions', 'customer_plan_id', 'customer_plans', 'id', 'fk_payment_transactions_customer_plan_id'),
        ('payment_transactions', 'subscription_plan_id', 'subscription_plans', 'id', 'fk_payment_transactions_subscription_plan_id'),
        ('refresh_tokens', 'user_id', 'users', 'id', 'fk_refresh_tokens_user_id'),
        ('refund_requests', 'customer_plan_id', 'customer_plans', 'id', 'fk_refund_requests_customer_plan_id'),
        ('refund_requests', 'legal_ticket_id', 'legal_tickets', 'id', 'fk_refund_requests_legal_ticket_id'),
        ('refund_requests', 'payment_transaction_id', 'payment_transactions', 'id', 'fk_refund_requests_payment_transaction_id'),
        ('refund_requests', 'requested_by_id', 'users', 'id', 'fk_refund_requests_requested_by_id'),
        ('subscription_usage', 'customer_plan_id', 'customer_plans', 'id', 'fk_subscription_usage_customer_plan_id'),
        ('system_notifications', 'recipient_user_id', 'users', 'id', 'fk_system_notifications_recipient_user_id'),
        ('ticket_attachments', 'uploaded_by_id', 'users', 'id', 'fk_ticket_attachments_uploaded_by_id'),
        ('ticket_audit_logs', 'actor_id', 'users', 'id', 'fk_ticket_audit_logs_actor_id'),
        ('ticket_context_snapshots', 'ticket_id', 'legal_tickets', 'id', 'fk_ticket_context_snapshots_ticket_id'),
        ('user_contracts', 'generation_job_id', 'contract_generation_jobs', 'id', 'fk_user_contracts_generation_job_id'),
        ('user_contracts', 'owner_id', 'users', 'id', 'fk_user_contracts_owner_id'),
        ('user_contracts', 'source_document_id', 'documents', 'id', 'fk_user_contracts_source_document_id'),
        ('user_contracts', 'template_id', 'contract_templates', 'id', 'fk_user_contracts_template_id'),
        ('user_contracts', 'workspace_id', 'workspaces', 'id', 'fk_user_contracts_workspace_id'),
        ('users', 'role_id', 'roles', 'id', 'fk_users_role_id'),
        ('workspaces', 'user_id', 'users', 'id', 'fk_workspaces_user_id')
        ) AS definitions(table_name, column_name, referenced_table, referenced_column, constraint_name)
    LOOP
        IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint constraint_row
            JOIN pg_class source_table ON source_table.oid = constraint_row.conrelid
            JOIN pg_class target_table ON target_table.oid = constraint_row.confrelid
            JOIN pg_attribute source_column ON source_column.attrelid = source_table.oid
                AND source_column.attnum = ANY (constraint_row.conkey)
            JOIN pg_attribute target_column ON target_column.attrelid = target_table.oid
                AND target_column.attnum = ANY (constraint_row.confkey)
            WHERE constraint_row.contype = 'f'
              AND source_table.relname = fk.table_name
              AND source_column.attname = fk.column_name
              AND target_table.relname = fk.referenced_table
              AND target_column.attname = fk.referenced_column
        ) THEN
            EXECUTE format(
                'ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY (%I) REFERENCES %I (%I) NOT VALID',
                fk.table_name, fk.constraint_name, fk.column_name,
                fk.referenced_table, fk.referenced_column
            );
        END IF;
    END LOOP;
END $$;
