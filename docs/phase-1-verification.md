# Phase 1 verification — 24 October 2026

**Result: successful.** Phase 1 adds versioned database migrations and reproducible
PostgreSQL setup. Later product phases have not been implemented.

Implementation verified at commit `0ecfc92afc5ee0e522fdead0da9c49355f2cbd90`.

## Environment

- Windows x64, JDK 26.0.1 compiling for Java 25.
- Spring Boot 4.1.1, Hibernate 7.4.5.Final, Flyway 12.4.0.
- Real PostgreSQL 17.11, isolated local test database bound to loopback.
- No existing application or production database was altered.

## Results

| Check | Result |
| --- | --- |
| Existing regression suite | 27 passed, zero failures/errors/skips |
| PostgreSQL migration integration suite | 5 passed, zero failures/errors/skips |
| Executable backend packaging | Successful |
| Compose YAML syntax | Parsed successfully |
| Docker Compose container startup | Not executed; Docker unavailable in verification environment |

The PostgreSQL checks verified:

1. All four migration versions apply to an empty schema; Hibernate validates all
   mapped entities and the HTTP server starts.
2. A real allocation workflow receives 50 units, reserves 20 and marks the request
   fulfilled.
3. Restart preserves organization/allocation data; repeat migration applies zero
   versions.
4. PostgreSQL rejects invalid quantities, duplicate stock and broken references.
5. A non-empty schema without migration history is refused without deleting its data.
6. An altered migration checksum is rejected.
7. A missing mapped column prevents startup; Hibernate does not recreate it.

Each integration test used a random owned schema and cleaned it up afterward.

## Commands

From `backend/`, the normal suite passed with `mvn test`. Packaging passed with
`mvn -DskipTests package`.

After correcting an error-message-dependent assertion to check Hibernate's
exception type, the final PostgreSQL suite passed with:

```text
mvn -Ppostgres-it test-compile failsafe:integration-test failsafe:verify
```

The complete reproducible command for both suites is
`mvn -Ppostgres-it verify`, with `PG_TEST_URL`, `PG_TEST_USERNAME` and
`PG_TEST_PASSWORD` supplied for a dedicated test database.
The initial combined run passed the 27 regression tests; its single assertion
failure was corrected and all five PostgreSQL checks then passed on rerun.

## Operational scope

Fresh databases can now be migrated automatically before backend startup.
Existing unmanaged schemas require the reviewed adoption process in
[the setup guide](database-setup.md). This result verifies database setup and
backend behavior; it is not a production deployment or authentication-provider
verification. Docker Compose is supplied as an optional development setup.
