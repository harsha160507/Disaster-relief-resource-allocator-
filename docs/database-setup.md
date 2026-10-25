# PostgreSQL setup and migrations

## Requirements

- JDK 25 or later and Maven (or the backend Maven wrapper).
- PostgreSQL 17. The optional Compose service uses the official PostgreSQL image.
- An empty application database/schema and credentials supplied through the environment.

Flyway creates/upgrades the tables before Hibernate checks their mappings.
Hibernate remains in `validate` mode; it does not create, alter or drop tables.
The JDBC driver and Flyway PostgreSQL support are runtime dependencies.
Identity-provider configuration and the optional first-administrator bootstrap are
covered in [the identity guide](identity-and-administration.md).

## Local development with Docker Compose

Run from the repository root. Set `DB_USERNAME` and `DB_PASSWORD` in your shell;
there is no committed password or insecure default. For PowerShell 7:

```powershell
$env:DB_USERNAME = "relief_dev"
$env:DB_PASSWORD = Read-Host "Local PostgreSQL password" -MaskInput
docker compose up -d --wait postgres
$env:DB_URL = "jdbc:postgresql://127.0.0.1:5432/disaster_relief"
Set-Location backend
.\mvnw.cmd spring-boot:run
```

On Bash, set the same variables using `export`, read the password with `read -rs`,
then run `./mvnw spring-boot:run` from `backend/`.
Use `DB_NAME` and `POSTGRES_PORT` to override the development database/host port;
update `DB_URL` to match.

The service binds only to loopback and keeps data in a named volume.
`docker compose stop postgres` stops it without deleting data.
Changing the password variable does not rotate credentials in an existing volume.
The Compose owner account is for local development; production should separate
migration privileges from application data access.

## Existing PostgreSQL server

Ask the database administrator to create an owner/login and an empty database.
For example, with an administrative PostgreSQL connection:

```text
createuser --pwprompt relief_owner
createdb --owner=relief_owner disaster_relief
```

Supply `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` in the backend's environment.
Do not put credentials in source, JDBC URLs committed to Git, or shell history.
The selected schema must allow the migration account to create tables, indexes
and constraints. The default application schema is PostgreSQL's `public` schema.

For separate migration/runtime accounts, supply `SPRING_FLYWAY_URL`,
`SPRING_FLYWAY_USER` and `SPRING_FLYWAY_PASSWORD` for the migration account.
The runtime account needs schema usage and data privileges for application tables.
For audit_events, grant SELECT and INSERT only, without UPDATE, DELETE, TRUNCATE
or DDL/trigger ownership. Arrange per-table grants for existing and future tables
through the DBA; see [audit protections](audit-history.md). No sequence privileges are needed for application-generated UUIDs.

After startup, `flyway_schema_history` should contain successful versions 1–7.
Restarting the backend validates checksums and applies only pending versions.

## Migration inventory

| Version | Purpose |
| --- | --- |
| V1 | Organizations, user profiles/roles, locations and resource types |
| V2 | Disasters, affected locations, resource requests and items |
| V3 | Inventory, shipments, allocations and audit events |
| V4 | Quantity/coordinate checks and workflow lookup indexes |
| V5 | Issuer-bound identities, administration versions/lock and disaster ownership |
| V6 | Audit attribution snapshots, lookup indexes and append-only protections |
| V7 | Shipment/allocation/request versions, delivered quantities and workflow checks |

The tables reflect the current JPA model, including UUIDs, timestamp precision,
numeric quantities, enum values, foreign keys and optimistic-lock versions.
Audit capture is implemented in Phase 3 and shipment application workflows in
Phase 4. See [shipment accounting and migration details](shipment-workflows.md).

## Safe upgrades and existing data

- Back up production data and verify restoration before deployment.
- Applied migrations are immutable. Add `V8__description.sql` and later versions
  for future changes; do not edit a version that has already been applied.
- Flyway checksum validation runs on startup. Incorrect/missing migration names
  and missing migration locations fail startup.
- Automatic baselining is disabled. A non-empty schema without Flyway history
  is rejected instead of being silently adopted.
- For a legacy populated schema, first reconcile its structure/data against
  all baseline versions in a restored copy. A DBA must review any adoption,
  corrective SQL and explicit baseline operation. Do not enable
  `baseline-on-migrate` to suppress an unexplained error.
- Flyway clean is disabled. Application startup never drops the database.
- Rollback uses a reviewed forward migration or a verified backup restore;
  destructive automatic down migrations are not provided.

## Verification

Fast regression tests use isolated H2 databases, with Flyway disabled:

```text
cd backend
./mvnw test
```

For migration verification, create a dedicated empty test database such as
`disaster_relief_test`. Supply `PG_TEST_URL`, `PG_TEST_USERNAME` and
`PG_TEST_PASSWORD` and run from `backend/`:

```text
./mvnw -Ppostgres-it verify
```

Use `mvnw.cmd` on Windows. The profile runs the normal suite plus sixteen PostgreSQL
integration tests. Missing database credentials fail the profile; these checks
are never silently skipped. Use a dedicated test account with CREATE permission
on the test database. Each test creates its own random `phase1_it_...` schema and
drops only that schema afterward; it does not use or drop `public` tables.

The checks cover fresh migration + Hibernate validation, an allocation workflow,
HTTP server startup, restart/data preservation, repeat migration, integrity
constraints, legacy-schema rejection, checksum/schema-drift rejection,
legacy identity/audit preservation, concurrent administrator safeguards,
append-only audit enforcement, atomic business/audit rollback, shipment
concurrency/stock conservation, a deterministic stale-inventory dispatch regression,
V6-to-V7 delivery progress preservation, and authenticated frontend workspace
query sorting and literal search. A passing H2 run alone does not validate PostgreSQL migrations.
