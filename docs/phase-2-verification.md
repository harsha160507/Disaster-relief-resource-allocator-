# Phase 2 verification — 24 October 2026

**Result: successful.** User/organization provisioning and access controls are
implemented. The tested implementation commit is
`b412cfb95051c5e19577f33d958d318cb09ea9e2`.

## Delivered

- Administrator-only organization and user creation, listing, reading and
  versioned updates; soft deactivation preserves historical references.
- Exact issuer/subject identity bindings, local database roles and current-user
  profile/organization endpoints.
- JWT signature, issuer, audience, expiration and subject checks. Roles in a token
  do not grant privileges. Every bearer request reloads the saved profile.
- Explicit first-administrator bootstrap; no default password or public signup.
- Database locking for concurrent bootstrap and last-active-administrator safety.
- Organization checks on disaster, location, request, inventory and allocation
  HTTP operations; allocation queries lock only stock within the request's organization.
- V5 migration preserving legacy records, deactivating unbound identities, adding
  versions/ownership and seeding the administrative transaction lock.
- [Deployment and API guide](identity-and-administration.md).

## Final verification

Command: `mvn -Ppostgres-it verify`, with the dedicated PostgreSQL test environment.

| Suite | Passed | Failures/errors/skips |
| --- | ---: | --- |
| Existing regression cases | 27 | 0 |
| New signed-token/provisioning/isolation cases | 16 | 0 |
| Unconfigured identity protection | 1 | 0 |
| Real PostgreSQL migration/integration cases | 7 | 0 |
| **Total** | **51** | **0** |

The complete build, executable packaging and verification succeeded in 1 minute
52 seconds using JDK 26.0.1 and PostgreSQL 17.11 on Windows.

Signed-token tests used a local RSA/JWKS provider and verified onboarding, own
profile access, token-role spoofing resistance, unknown/disabled identity denial,
wrong issuer/audience/signature rejection, missing/expired claim rejection,
role revocation with reused tokens, organization deactivation, duplicate/stale
edit conflicts and cross-organization access denial. A separate test verified
that standard Boot decoder properties cannot bypass the application's required
identity configuration.

PostgreSQL checks verified fresh migrations and backend startup, allocation,
restart/data preservation, invalid data rejection, checksum/schema-drift safety,
V4-to-V5 legacy identity preservation/deactivation, concurrent first-admin
bootstrap and concurrent attempts to disable the last two administrators.

## Deployment requirements and boundaries

A live deployment still needs its own identity provider's issuer/audience,
provider-side client configuration and verified user subjects. No external
identity-provider account or production database was changed.

Before upgrading existing data, review duplicate organization codes. V5 disables
legacy profiles without an issuer binding; explicitly provision verified
replacement identities. Legacy unowned disasters remain administrator-only.

This phase does not add frontend login screens, shipment workflows, audit
capture or CI. Those remain later phases.
