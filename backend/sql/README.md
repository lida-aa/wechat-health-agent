# Database schema

`src/main/resources/schema.sql` is the canonical schema for a new health-agent database.

For an existing demo database, apply the non-destructive scripts in order:

1. `migrations/V2__health_reminder.sql`
2. `migrations/V3__health_record_document.sql`
3. `migrations/V4__health_agent_schema_alignment.sql`

The application does not automatically execute the files under `sql/migrations`.
Apply them through the normal database release process after taking a backup.

## Administrator bootstrap

The schema no longer inserts a shared default administrator. Configure both
`HEALTH_ADMIN_USERNAME` and `HEALTH_ADMIN_PASSWORD` before startup. The password
must contain at least 12 characters and is stored as a BCrypt hash.

Set `HEALTH_AGENT_JWT_SECRET` to a stable secret of at least 32 characters in a
deployed environment. Without it, the application generates an in-memory key,
so administrator tokens become invalid after restart.

## Legacy cleanup

`optional/drop_legacy_demo_tables.sql` is deliberately not part of the migration
sequence. It permanently removes tables used by the old multi-purpose demo.
Run it only after confirming that health records have migrated successfully and
that the old media data is no longer required.

The old multi-purpose `sql/schema.sql` archive is intentionally excluded from
this clean repository so there is only one valid initialization schema.
