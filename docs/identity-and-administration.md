# Phase 2: identities, organizations and administration

## Authentication model

The backend is an OAuth2 resource server. Users sign in through your external
OpenID Connect/OAuth2 provider; the backend does not issue passwords or access
tokens. The browser login screen belongs to the later frontend phase.

Configure a trusted issuer and this API's audience in the deployment environment:

```text
SECURITY_OAUTH2_ISSUER_URI=https://<your-provider>/<issuer>
SECURITY_OAUTH2_AUDIENCE=<this-api-audience>
```

The issuer must publish provider metadata for key discovery. If discovery is not
available, also set `SECURITY_OAUTH2_JWK_SET_URI` to its JWKS endpoint.
Issuer validation still applies when supplying JWKS directly. Endpoints require
HTTPS, except loopback addresses for local development/testing.

Tokens must have a valid signature, matching issuer and audience, nonblank
subject, expiration, and valid time bounds. Only the default trusted RS256
algorithm is accepted. A token's `roles` claim does **not** grant application
permissions. Each bearer request reloads the active local profile and roles,
bound to the exact `(issuer, subject)` pair. Unknown/unbound profiles, disabled
users and members of disabled organizations receive 401.

Without a configured issuer, protected APIs remain inaccessible; no basic-auth
login, default password, self-registration or development bypass is enabled.
A configured issuer without an audience prevents startup.

## First administrator

Create the intended administrator identity in your identity provider and obtain
its stable subject identifier (`sub`), not its email address or display name.
For one controlled backend startup, set:

```text
SECURITY_BOOTSTRAP_ENABLED=true
SECURITY_BOOTSTRAP_ADMIN_SUBJECT=<exact-subject-from-your-provider>
SECURITY_BOOTSTRAP_ADMIN_DISPLAY_NAME=<administrator-display-name>
```

Keep the issuer/audience configuration above. Bootstrap creates a global
SYSTEM_ADMIN profile only when no active administrator exists for that issuer.
It never changes an existing identity into an administrator. Remove/disable the
bootstrap settings after successful provisioning.

The bootstrap and administration APIs share a database row lock, so concurrent
application replicas cannot bootstrap different administrators simultaneously or
disable both of the last two administrators. APIs refuse to deactivate or remove
the role of the last active administrator. Provision a replacement administrator
before disabling the previous one.

Global administrators have only SYSTEM_ADMIN and no organization. All other users
require a selected organization and at least one local role. Existing bound
identities are immutable through the update API; create an explicit replacement
profile when changing a person's identity provider binding.

## Provision organizations and users

Send the administrator's provider-issued access token as:

```text
Authorization: Bearer <access-token>
Content-Type: application/json
```

1. `POST /api/v1/admin/organizations` with:
   `{"name":"Relief North","code":"NORTH","contactEmail":"office@example.org"}`.
   Save the returned organization ID.
2. `POST /api/v1/admin/users` with:
   `{"issuer":"<configured-issuer>","subject":"<provider-subject>","displayName":"Field Agent","organizationId":"<organization-id>","roles":["FIELD_AGENT"]}`.
3. The user can now call `GET /api/v1/me` and
   `GET /api/v1/me/organization` using their own provider token.

Allowed roles are SYSTEM_ADMIN, DISASTER_COORDINATOR, INVENTORY_MANAGER,
FIELD_AGENT, PARTNER_MANAGER and VIEWER. Only a system administrator can provision
users, change roles, move users between organizations or manage organizations.

## Administration API

| Method and path | Behavior |
| --- | --- |
| POST /api/v1/admin/organizations | Create an organization |
| GET /api/v1/admin/organizations?page=0&size=20 | List organizations |
| GET /api/v1/admin/organizations/{id} | Read an organization |
| PUT /api/v1/admin/organizations/{id} | Update metadata/active state with version |
| POST /api/v1/admin/users | Bind a provider identity to a new local profile |
| GET /api/v1/admin/users?page=0&size=20 | List profiles |
| GET /api/v1/admin/users/{id} | Read a profile |
| PUT /api/v1/admin/users/{id} | Update metadata, membership, roles and active state with version |
| GET /api/v1/me | Read the caller's profile and local roles |
| GET /api/v1/me/organization | Read the caller's organization; global admins have none |

Pages are zero-based, default to 20 records and allow sizes 1–100. List responses
contain `items`, `page`, `size`, `totalElements` and `totalPages`.

PUT replaces the mutable fields. Organization input includes `name`, optional
`code`/`contactEmail`, `active` and `version`. User input includes
`displayName`, optional `email`, `organizationId`, `roles`, `active` and
`version`. Read the latest response's version before editing; stale edits return
409. Deactivation preserves historical references. Roles and active status are
checked again on the next request, even when reusing an existing token.

Duplicate identity pairs and case-insensitive organization codes return 409.
Malformed/invalid fields return 400; invalid lifecycle/membership operations
return 422. Authentication failures return 401 and forbidden operations return
403, using the existing structured error format.

## Organization boundaries

- Administrators can manage all organizations. Operational writes still require
  compatible data: a request, destination and allocated stock must belong to the
  same organization.
- Newly created disasters have an owning organization. Organization members
  default to their own; global admins must supply `organizationId`.
- Disaster and location operations check organization ownership. An affected
  location cannot be attached to a disaster from another organization.
- Request creation uses the caller's own requester profile, except for explicit
  administrator actions. Destination and requester organizations must match.
- Organization members can read their organization's requests. Field agents can
  edit/submit only their own; partner managers can manage their organization's
  requests. Existing role checks still govern approvals and allocations.
- Stock availability and allocation queries filter by organization in the
  database. Automatic allocation cannot consume another organization's stock.
- Resource types remain a shared catalog, governed by the existing inventory
  manager/system administrator permissions.
- Services used internally by scheduled/admin code are trusted application
  operations. HTTP entry points enforce principal, role and organization access;
  callers must never expose repository/service methods as unguarded new routes.

## Upgrade from Phase 1

V5 adds issuer bindings, organization/user versions, disaster ownership and the
administrative lock. It preserves existing records. Legacy profiles without an
issuer are deactivated; explicitly provision their verified replacement
identities. No issuer is guessed from an existing subject.

Legacy disasters have no inferred owner and remain system-administrator-only.
Review their ownership before adopting them into an organization.

Before deployment, check for legacy organization codes that differ only by case:

```sql
SELECT lower(code), count(*)
FROM organizations
WHERE code IS NOT NULL
GROUP BY lower(code)
HAVING count(*) > 1;
```

Resolve any duplicates with a reviewed data change before V5 creates the unique
index. Back up and verify restoration as described in the database setup guide.

## Verification

`mvn test` runs the regression suite plus signed-token security/provisioning
tests against a local RSA/JWKS identity-provider fixture.

With the dedicated PostgreSQL test environment configured,
`mvn -Ppostgres-it verify` also checks V4-to-V5 data preservation and concurrent
bootstrap/last-administrator protection on real PostgreSQL.

A real deployment still needs your provider's issuer, audience, client setup and
user subjects. The tests do not create or configure an external provider account.
