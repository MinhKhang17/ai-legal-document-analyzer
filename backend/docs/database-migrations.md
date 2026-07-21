# Database migrations

The backend uses Flyway for PostgreSQL schema changes. Hibernate is configured with
`ddl-auto=validate`; it must not create, drop, or truncate application tables.

The complete entity baseline is split into three safe layers:

- `V20260717_00`: creates all current JPA entity tables and core unique indexes for a new database.
- `V20260720_02`: adds every current entity column when an older database is missing it, without rejecting existing rows.
- `V20260720_03`: adds missing foreign keys after checking PostgreSQL metadata, so legacy constraints with different names are not duplicated.

`out-of-order=true` is enabled for this one-time baseline rollout so a server that
already recorded one of the later timestamp migrations can still apply the additive
`V20260717_00` baseline. New migrations must still use a version greater than the
latest committed migration.

DTO classes do not map database objects and therefore do not require SQL migrations.

For each schema change, add a new versioned SQL file under
`src/main/resources/db/migration` (for example
`V20260721_01__short_description.sql`). Prefer additive, repeat-safe statements such
as `CREATE TABLE IF NOT EXISTS`, `ADD COLUMN IF NOT EXISTS`, and
`CREATE INDEX IF NOT EXISTS`. Never edit an already-applied migration.

Before deployment, confirm that the backend points to the intended database through
`DB_URL`, rebuild the backend image so the migration is packaged, and check
`flyway_schema_history` after startup. Existing databases are baselined on first
Flyway startup; no application data is removed.

`EntityMigrationCoverageTest` fails when a new entity table or column is added without
being covered by a `CREATE TABLE`/`ADD COLUMN` statement in *some* migration file — it
scans every file under `db/migration`, so a new column can land in its own dedicated
migration instead of requiring an edit to `V20260720_02`.

## Why the schema was inconsistent

`ChatMessageFeedback` is a valid `@Entity` in the application's scanned
`com.analyzer.api` package and maps to `chat_message_feedbacks`. The unreliable
behavior came from environment drift: the committed default was `create-drop`, the
example environment used `update`, and the local backend environment also selected
`create-drop`. Container images could therefore run a different schema policy or an
older packaged entity set. Always verify `JPA_DDL_AUTO=validate`, the active profile,
the resolved `DB_URL`, and that the backend image was rebuilt after adding a
migration.
