UPDATE customer_plans cp
SET status = 'EXPIRED'
WHERE cp.status = 'ACTIVE'
  AND cp.id NOT IN (
    SELECT DISTINCT ON (customer_id) id
    FROM customer_plans
    WHERE status = 'ACTIVE'
    ORDER BY customer_id, start_date DESC NULLS LAST, id DESC
  );

CREATE UNIQUE INDEX IF NOT EXISTS uk_customer_plan_one_active_per_customer
  ON customer_plans (customer_id) WHERE status = 'ACTIVE';
