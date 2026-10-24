# Phase 4 verification — 24 October 2026

**Result: successful.** Shipment creation, dispatch, delivery and cancellation
before dispatch are implemented. The verified implementation commit is
`b2ba6ab99b33c6dcc3e05dd09e29ceb239b50b12`.
Only this report was added afterward.

## Delivered

- Validated manifests containing 1–100 distinct reserved allocations for one
  destination, with exclusive assignment and unique tracking references.
- CREATED → DISPATCHED → DELIVERED and CREATED → CANCELLED workflows.
- Physical stock deduction exactly once at dispatch; delivery changes delivery
  progress without deducting stock again.
- Cancellation releases reservations and item allocations, allowing new
  allocation while retaining the cancelled shipment's history.
- Separate item delivered quantities and PARTIALLY_DELIVERED / DELIVERED request
  states; previous allocation-based FULFILLED behavior remains compatible.
- Shipment/request/allocation versions, ordered row locking and consistent
  allocation inventory-lock order.
- Scoped, bounded shipment list/detail APIs; role-based logistics operations
  and field-agent delivery confirmation.
- Transactional shipment, allocation, inventory, item and request audit capture,
  without tracking references in audit details.
- V7 migration and [workflow/API/accounting guide](shipment-workflows.md).

Every project file was created or updated directly through GitHub. The phase
contains 31 focused commits on main, including this report; the local checkout
was synchronized afterward for verification.

## Final verification

Command: `mvn -Ppostgres-it verify` with a dedicated PostgreSQL test database.

| Suite | Passed | Failures/errors/skips |
| --- | ---: | --- |
| Earlier normal regression/security/audit cases | 53 | 0 |
| New shipment workflow/access/legacy-data cases | 12 | 0 |
| Real PostgreSQL migration/integration cases | 14 | 0 |
| **Total** | **79** | **0** |

The full build, executable packaging and verification succeeded in 3 minutes
15 seconds using JDK 26.0.1 and PostgreSQL 17.11 on Windows.
Diff whitespace checks passed, documentation links resolved, and the local
checkout was clean. The temporary PostgreSQL server was stopped after testing.

Signed-token HTTP checks verified shipment creation, dispatch and field-agent
delivery, role restrictions, cross-organization denials, scoped lists,
malformed manifests, duplicate references, repeated/invalid transitions and
administrator audit searches. Service integration checks covered split delivery,
cancellation and reallocation, multiple stock locations/requests, and rejected
legacy resource/quantity inconsistencies.

## Verified accounting example

The signed-token workflow reserved and delivered 20 litres from 50:

| Step | Physical | Reserved | Item allocated | Item delivered |
| --- | ---: | ---: | ---: | ---: |
| Reservation | 50 | 20 | 20 | 0 |
| Shipment created | 50 | 20 | 20 | 0 |
| Dispatched | 30 | 0 | 20 | 0 |
| Delivered | 30 | 0 | 20 | 20 |

Separate checks cancelled two pending allocations of 8 and 12 litres together:
physical stock remained 50, reservations became zero and the request returned
to APPROVED. Reallocation created a new allocation rather than reusing cancelled
records. A split-delivery check preserved 8 delivered litres while cancelling
the remaining pending shipment, then reallocated and delivered the final 12.

## PostgreSQL concurrency and rollback evidence

- Two concurrent attempts to assign one allocation produced exactly one shipment.
- Concurrent duplicate dispatch and duplicate delivery each had one successful
  transition; stock and delivery totals changed once.
- Dispatch racing cancellation produced one winner and conserved the appropriate
  physical/reserved balances.
- Allocation of another request during dispatch reserved only the remaining
  30 litres; physical and reserved balances both ended at 30.
- A forced shipment audit-append failure rolled back physical stock, reservations,
  allocation status, shipment status and all preceding audit writes in that
  transaction. The same shipment completed after removing the test constraint.
- Delivered shipment and stock state survived backend restart.
- A V6 delivered-data fixture upgraded to V7 delivery totals/status without
  deducting physical stock again. Invalid delivery totals and reversed timestamps
  were rejected.
- Earlier fresh-start, integrity, checksum/schema-drift, legacy preservation,
  audit immutability and administrator concurrency checks continued to pass.

All database checks created and removed their own random disposable schemas.
No production database or external identity-provider account was changed.

## Operational boundaries

Cancellation applies before dispatch. Returns, post-dispatch cancellation,
loss reconciliation and partial receipt of one manifest need separate workflows.
Existing FAILED or inconsistent legacy shipments require review. V7 preserves
legacy timestamp/link violations using NOT VALID constraints while enforcing
those checks on new or updated rows; inconsistent delivered totals fail migration
for explicit reconciliation.

Application locking and audit guarantees assume writes use the documented
services. Direct SQL/repository changes bypass that workflow. Frontend and CI
remain separate phases.
