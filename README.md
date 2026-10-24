# Disaster Relief Resource Allocator

A Spring Boot backend for disaster response, inventory, resource requests and allocations.

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

## Current scope

This repository currently contains the backend. Frontend, user/organization
provisioning APIs, shipment operations, audit capture and CI are planned later
phases. Normal authenticated API access also requires an external JWT issuer
configured with `security.oauth2.issuer-uri`; without one, protected endpoints
remain inaccessible. Database setup alone does not create users or login sessions.
