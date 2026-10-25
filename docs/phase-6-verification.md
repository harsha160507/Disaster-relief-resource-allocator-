# Phase 6 verification

Verified on 25 October 2026.

## Result

Automated CI and release-candidate preparation succeeded on GitHub Actions.
All project additions and edits were committed directly to main through GitHub.
This phase has 15 commits from base 2b7592b8cb027077016475c6cf673e2830fb5aad,
including this report.

The implementation was verified at commit
4ddbbdd981a54aa840a6bcd3cb0e7c1e58b8cc2d by
[CI run 40181270833](https://github.com/harsha160507/Disaster-relief-resource-allocator-/actions/runs/40181270833).
All five jobs passed: Backend and PostgreSQL, Frontend, Release tooling,
Release candidate and CI gate. Subsequent database-guide/report changes are
documentation only and trigger the same complete CI workflow. Consult the
[Actions page](https://github.com/harsha160507/Disaster-relief-resource-allocator-/actions/workflows/ci.yml)
for the latest commit's result.

## Evidence

| Verification | Result |
| --- | --- |
| Backend regular tests, Temurin JDK 25 | 72 passed |
| Real PostgreSQL 17.11 integration tests | 16 passed |
| Frontend tests, Node 24 | 15 passed |
| Release packaging tests | 5 passed |
| Total automated tests | 108 passed, zero failures/skips |
| Packaged executable JAR smoke test | Startup/migrations succeeded; anonymous protected API returned structured 401 |
| Frontend locked install and formatting | Passed |
| Dependency audit | Zero npm vulnerabilities reported |
| Strict TypeScript and Vite production build | Passed |
| Release candidate assembly/upload | Passed |
| Downloaded release candidate | ZIP checksum and all 17 payload-file hashes/lengths independently verified |
| Local release-tooling tests | 5 passed on Windows |
| Local backend compilation and whitespace checks | Passed |

The verified run uploaded backend/frontend binaries, test reports, and
[the release candidate](https://github.com/harsha160507/Disaster-relief-resource-allocator-/actions/runs/40181270833/artifacts/12594540213).
Artifacts expire according to the retention policy documented in release-readiness.md.

## Concurrency regression found and fixed

The first two Linux CI runs exposed a real shipment race: validation had loaded
inventory before another transaction changed its version. Upgrading that stale
instance to a write lock could raise an optimistic-locking error during dispatch.
The CI gate failed and candidate packaging was skipped, as intended.

Shipment dispatch/cancellation now refresh stock under pessimistic write locks in
stable inventory-ID order before any stock mutation. This retains committed
receipts/reservations instead of operating on the old snapshot. A deterministic
PostgreSQL regression preloads stock, commits a receipt from another transaction,
then dispatches and verifies conserved quantities and exactly one dispatch event.
The existing simultaneous allocation/dispatch test and full regression suite also
passed. No migration or test assertions were relaxed.

## Delivered controls

- Main-push, pull-request and manual CI triggers; complete checks even for docs.
- Time limits, superseded-run cancellation, read-only repository token and
  full-commit-pinned official Actions.
- Isolated PostgreSQL service with disposable credentials; no production secrets.
- Failure reports, a required aggregate gate suitable for branch rules, and
  release packaging only after all preceding checks pass.
- Weekly Dependabot configuration for Maven, npm and GitHub Actions; no automatic merges.
- Source-identified release ZIP, manifest, SHA-256 checksums and tested packaging.
- Runtime environment template, review template and deployment/rollback runbook.

## Production acceptance still required

This phase prepares candidates; it does not deploy or publish a GitHub Release.
The verified manifest has identity_configured=false and deployment_approved=false.
Actual provider login/callback, target hosting/TLS/reverse proxy, production
database permissions and backup restoration, real-account tenant/workflow
acceptance, monitoring and operational ownership still require the chosen target
environment. See release-readiness.md for the exact checklist.

Repository variables, branch protection and Dependabot-alert settings are not
silently configured. The guide describes maintainer setup; the current direct-main
working agreement remains in effect. Pull-request triggers are configured and
reviewed, but the live verification above exercised the main-push path.
