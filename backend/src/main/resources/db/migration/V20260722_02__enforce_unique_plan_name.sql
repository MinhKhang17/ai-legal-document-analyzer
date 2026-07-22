WITH ranked AS (
  SELECT id, plan_name,
         ROW_NUMBER() OVER (PARTITION BY lower(plan_name) ORDER BY id) AS rn
  FROM subscription_plans
)
UPDATE subscription_plans sp
SET plan_name = sp.plan_name || ' (' || ranked.rn || ')'
FROM ranked
WHERE sp.id = ranked.id AND ranked.rn > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uk_subscription_plan_name_lower
  ON subscription_plans (lower(plan_name));
