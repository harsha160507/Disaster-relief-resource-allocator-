# Disaster Relief Resource Allocator

## PostgreSQL deployment

The backend uses PostgreSQL in production. The PostgreSQL JDBC driver is included
as a runtime dependency, while H2 is restricted to the test scope.

Set these environment variables before starting the backend:

```text
DB_URL=jdbc:postgresql://<host>:<port>/<database>
DB_USERNAME=<database-user>
DB_PASSWORD=<database-password>
```

The application reads these variables through Spring configuration. Credentials
must be supplied by the deployment environment or a secrets manager; do not put
them in source files, `.properties` files, or committed container configuration.

Production uses `spring.jpa.hibernate.ddl-auto=validate`, so it verifies the
existing PostgreSQL schema without creating, altering, or dropping data. The
database must therefore be provisioned before startup.

The repository does not currently contain Flyway or Liquibase migrations. A
versioned migration tool should be introduced before the first production
deployment, with an initial baseline migration generated and reviewed from the
JPA schema, followed by forward-only migrations for future changes. Until then,
schema creation and upgrades are an explicit deployment responsibility.

Tests use an isolated in-memory H2 database through
`backend/src/test/resources/application.properties`; this test configuration is
not used by production.