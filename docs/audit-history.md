# Phase 3: audit history

Successful changes made through application services now append audit events in
the same database transaction as the business change. An audit append failure
rolls back the business operation. Failed validation, denied requests, rolled-back
transactions and supported no-op updates do not create success records.

## Covered changes

| Area | Recorded operations |
| --- | --- |
| Administration | Organization creation/updates; user creation, role/membership/activation changes; first-administrator bootstrap |
| Disasters | Creation, activation, closure and addition of an affected location |
| Locations/catalog | Location creation and shared resource-type creation |
| Inventory | Receipts and quantity adjustments with before/after quantities |
| Requests | Creation, added resource items, submission, approval and rejection |
| Allocation | Each reservation, before/after inventory reservations and item allocations, and resulting request fulfillment transitions |

Reservations carry request, item and inventory identifiers. Item additions are
recorded against their parent request. All writes for one HTTP request share a
server-generated correlation UUID; client correlation headers cannot override it.
Internal service calls without an HTTP request have no correlation UUID.

## Attribution and privacy

Each event has an immutable UUID, UTC creation time, action, entity type/id,
operation, optional owning organization and source:

- `USER`: actor profile UUID and snapshots of local roles and organization from
  the authenticated local principal. JWT role claims and request-body actor fields
  never grant attribution.
- `SYSTEM_INTERNAL`: trusted service invocation without an authenticated user.
- `BOOTSTRAP`: explicit first-administrator provisioning before a user login exists.
- `LEGACY`: events that existed before migration V6; their previous contents are
  preserved without inventing historical attribution.

Actor roles and organization are historical snapshots, independent of later
profile edits. User-update details include both previous and new membership.
The event's owning organization for a user update is the new organization;
the previous organization remains in the structured details.

New details use explicit allowlists. They capture statuses, roles, active flags,
quantities, related UUIDs and changed field names. They exclude bearer tokens,
passwords, issuer/subject values, emails, names, addresses, coordinates, notes and
descriptions. Whole entities and request bodies are never serialized. Existing
legacy details are preserved and remain visible only to administrators.

## Read-only API

Only an active, provisioned `SYSTEM_ADMIN` can use these endpoints:

```text
GET /api/v1/admin/audit-events
GET /api/v1/admin/audit-events/{eventId}
```

The list response contains `content`, `page`, `size`, `totalElements` and
`totalPages`. Defaults are page 0 and size 20. Size must be 1–100; page must be
0–100000. Results sort by creation time descending, then UUID descending to
give equal timestamps a stable order.

Optional filters are combined with AND:

| Parameter | Value |
| --- | --- |
| entityType | ORGANIZATION, USER_PROFILE, DISASTER, LOCATION, RESOURCE_TYPE, INVENTORY, RESOURCE_REQUEST, ALLOCATION |
| entityId | Entity UUID |
| actorId | Local actor profile UUID |
| organizationId | Historical owning organization UUID |
| action | CREATED, UPDATED, STATUS_CHANGED, DELETED |
| from / to | Inclusive ISO-8601 UTC instants; from must not exceed to |
| correlationId | Server-generated correlation UUID |

Unknown historical entity types remain available in unfiltered lists and by
event UUID. `details` is a string containing JSON for newly captured events;
legacy details may be plain text. Unauthenticated calls return 401, other roles
403, invalid filters 400, unknown event UUIDs 404 and unsupported write methods
405. Reading history does not append another audit event.

Example, with an administrator token supplied securely in the environment:

```sh
curl -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$API_BASE/api/v1/admin/audit-events?entityType=RESOURCE_REQUEST&size=20"
```

## Database and operational guarantees

Migration V6 adds attribution columns and indexes without changing earlier
migration files. PostgreSQL triggers reject UPDATE, DELETE and TRUNCATE on the
audit table with SQLSTATE 55000. The JPA entity is immutable and there is no audit
write/delete API. Records use the application's UTC clock; synchronize application
hosts with a trusted time source.

Use separate migration and runtime database roles. The runtime role needs SELECT
and INSERT on audit_events, without UPDATE, DELETE, TRUNCATE, trigger/DDL ownership
or privileges to disable the protections. Provision object privileges after
migrations using the deployment's schema and role names. A database owner can
alter protections; this is an application audit trail, not cryptographic tamper
evidence.

Normal tests use H2 and cannot prove PostgreSQL trigger behavior. Run
`./mvnw -Ppostgres-it verify` with the dedicated PostgreSQL test environment
described in [database setup](database-setup.md) to check migrations, preservation,
append-only enforcement, committed persistence and atomic rollback.

Direct repository/SQL changes outside application services are not automatically
captured. Authentication failures, read access, external identity-provider events,
and future shipment operations are outside this phase. No frontend or scheduled
retention/deletion job is introduced.
