# Disaster Relief Resource Allocator

A desktop React/TypeScript workspace and Spring Boot backend for disaster response,
inventory, requests, allocation and shipment delivery.

## Database setup

The backend uses PostgreSQL. Flyway now creates the initial application schema
and applies versioned upgrades before Hibernate validates the entity mappings.
Start with an empty database/schema; the database itself must be provisioned first.

Required environment variables:

```text
DB_URL=jdbc:postgresql://<host>:<port>/<database>
DB_USERNAME=<database-user>
DB_PASSWORD=<database-password>
```

Supply credentials through the deployment environment or a secrets manager.
Never commit real credentials.

See [Database setup and migration guide](docs/database-setup.md) for local
PostgreSQL with Docker Compose, existing-server provisioning, production account
separation, migration safety, and the PostgreSQL integration-test command.

## Run and test

From `backend/`, using JDK 25 or later:

```text
./mvnw spring-boot:run
./mvnw test
```

Use `mvnw.cmd` on Windows. Normal tests use an isolated H2 database with Flyway
disabled. To verify real PostgreSQL migrations, set `PG_TEST_URL`,
`PG_TEST_USERNAME` and `PG_TEST_PASSWORD` for a dedicated test database, then run:

```text
./mvnw -Ppostgres-it verify
```

Production keeps `spring.jpa.hibernate.ddl-auto=validate`, checksum validation,
automatic-baseline protection and Flyway clean protection enabled.
Applied migrations must remain unchanged; add a new version for future changes.

## Users and organizations

Phase 2 adds administrator-controlled organization/user provisioning,
issuer-and-subject identity bindings, database-managed roles, current-profile
endpoints, deactivation, and organization access checks.

See [Identity and administration setup](docs/identity-and-administration.md) for
issuer/audience configuration, first-administrator bootstrap and API examples.

## Audit history

Phase 3 records successful administrative and relief-workflow changes with trusted
actor attribution, historical roles/organization, and privacy-conscious details.
Administrators can search a bounded, read-only history API. PostgreSQL protects
stored events from updates, deletes and truncation.

See [Audit history guide](docs/audit-history.md) for coverage, filters, transaction
guarantees and operational boundaries.

## Shipment workflows

Phase 4 connects reserved allocations to shipment creation, dispatch, delivery and
cancellation. Stock leaves inventory once at dispatch; actual delivery progress
is tracked separately from allocation. Shipment operations enforce organization
access and append transactional audit history.

See [Shipment workflow guide](docs/shipment-workflows.md) for accounting, role
permissions, API contracts and migration details.

## Desktop frontend

Phase 5 adds a desktop inventory-manager dashboard and operational screens for
inventory, requests, allocations, shipments, disasters, locations, catalogues,
organizations, users and audit history. It uses the supplied UI components, a text
logo, blue accents and saved light/dark themes.

From frontend/, run `npm ci` and `npm run dev`. Organization sign-in uses a
configured public OIDC client; an explicitly labelled interactive demo is also
available. Production requires the identity-provider settings and a same-origin
API reverse proxy described in the guide.

See [Frontend setup](docs/frontend-setup.md) and
[Supplied component integration](docs/frontend-components.md).

## Current scope

This repository contains the backend and desktop frontend. Automated CI remains
Phase 6. Normal authenticated API access also requires an external JWT issuer
configured with `security.oauth2.issuer-uri` and `security.oauth2.audience`, plus a provisioned local profile; without these, protected endpoints
remain inaccessible. Database setup alone does not create users or login sessions.
