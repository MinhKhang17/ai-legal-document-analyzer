# Database Convention

## 1. General Rules

* Use snake_case.
* Use English only.
* Avoid abbreviations.
* Table names must be plural.

---

# 2. Table Naming

Good

```sql
users
orders
products
order_items
payment_transactions
```

Bad

```sql
User
tbl_user
USERS
```

---

# 3. Column Naming

Use snake_case.

Good

```sql
first_name
last_name
customer_id
created_at
```

Bad

```sql
firstName
FirstName
FIRST_NAME
```

---

# 4. Primary Key

All tables use:

```sql
id
```

Example

```sql
users.id
orders.id
```

---

# 5. Foreign Key

Format

```sql
<entity>_id
```

Examples

```sql
user_id
order_id
product_id
role_id
```

---

# 6. Audit Columns

Mandatory

```sql
created_at
created_by
updated_at
updated_by
```

Optional

```sql
deleted_at
deleted_by
```

---

# 7. Index Naming

Format

```sql
idx_<table>_<column>
```

Examples

```sql
idx_users_email

idx_orders_customer_id
```

---

# 8. Unique Constraint Naming

Format

```sql
uk_<table>_<column>
```

Examples

```sql
uk_users_email

uk_roles_code
```

---

# 9. Foreign Key Constraint Naming

Format

```sql
fk_<source_table>_<target_table>
```

Examples

```sql
fk_orders_users

fk_order_items_orders
```

---

# 10. Soft Delete

Preferred strategy

```sql
deleted_at
deleted_by
```

Do not physically delete business data.

---

# 11. Migration Naming

Format

```text
V{version}__{description}.sql
```

Examples

```text
V1__create_users.sql

V2__create_orders.sql

V3__add_status_to_users.sql
```

---

# 12. Data Type Standard

ID

```sql
BIGINT
```

Timestamp

```sql
TIMESTAMP
```

Amount

```sql
DECIMAL(18,2)
```

Boolean

```sql
BOOLEAN
```

Avoid

```sql
FLOAT
DOUBLE
```

for financial data.