# CI and release readiness

## Automated checks

The CI workflow runs on every pull request, every push to main and manual runs.
It has no path filters, so documentation-only changes also produce a complete
result for the commit. Newer runs for the same branch cancel superseded runs.

- **Backend and PostgreSQL** uses Temurin JDK 25, the checked-in Maven wrapper
  and an isolated PostgreSQL 17 service. It runs all regular and PostgreSQL
  integration tests, packages the executable JAR, and starts that JAR against
  the disposable database. The smoke check requires a structured 401 response
  from the protected current-profile endpoint without an access token.
- **Frontend** uses Node 24 and npm ci from the committed lockfile. Formatting,
  all frontend tests, a high/critical dependency vulnerability gate (including
  development dependencies), and the strict TypeScript/production build must pass.
  The npm audit gate uses the current registry advisory database and fails if
  the audit request itself fails.
- **Release tooling** verifies archive contents, file hashes, repeatability,
  required inputs and failure handling.
- **Release candidate** runs only after those jobs succeed, on main pushes and
  manual runs. Pull requests never assemble deployable candidates.
- **CI gate** requires every applicable job to succeed, including packaging on
  non-PR runs. A failed, cancelled or unexpectedly skipped dependency fails this gate.

Actions are pinned to full upstream commit hashes. Dependabot proposes updates
for Actions, Maven and npm weekly; it does not merge or deploy them automatically.
The workflow token has read-only repository contents permission, checkout does
not retain credentials, and CI uses no production credentials. The PostgreSQL
password in the workflow is deliberately disposable test-only data.

Backend dependency review is supplied through Dependabot alerts/update proposals;
there is no claim of a blocking full Maven vulnerability scan. Enable GitHub
Dependabot alerts in repository settings if they are not already enabled.

## Build configuration and artifacts

The default artifacts have organization sign-in disabled until public identity
configuration is supplied. They remain useful for inspecting the explicit demo.
For a target environment set these **Actions repository variables**, then run CI
manually on the intended commit:

| Variable | Value |
| --- | --- |
| VITE_OIDC_AUTHORITY | Trusted HTTPS issuer/authority |
| VITE_OIDC_CLIENT_ID | Public SPA client ID |
| VITE_OIDC_SCOPE | Provider scopes; defaults to openid profile email |
| VITE_OIDC_AUDIENCE | Optional provider-specific API audience |

These values are public and baked into JavaScript. Never place a client secret,
password, access token or private signing key in them. The backend still needs
its own runtime issuer/audience and database settings. The supported deployment
uses a same-origin /api reverse proxy; the candidate uses an empty API base URL.

A successful main/manual run uploads:

- backend-test-reports and frontend-test-reports, retained for 14 days.
- backend-package and frontend-package, retained for 14 days.
- relief-release-candidate-<full-commit>, retained for 30 days.

The candidate contains a ZIP and SHA256SUMS. Inside the ZIP are
backend/application.jar, frontend/, docs/, README.md and manifest.json.
The manifest records the exact source commit, Actions run ID, public frontend
configuration and SHA-256/byte length of each payload file. It deliberately marks
deployment_approved as false. identity_configured indicates only that authority
and client ID were supplied, not that provider sign-in was tested.

After downloading and extracting the Actions artifact wrapper, verify:

```sh
sha256sum -c SHA256SUMS
unzip disaster-relief-<commit-prefix>.zip -d candidate
```

On Windows, compare Get-FileHash -Algorithm SHA256 for the ZIP with SHA256SUMS.
Preserve the ZIP, checksum file and CI run URL with the release record before the
artifact expires. The application still has development package versions
(backend 0.0.1-SNAPSHOT and frontend 0.1.0); candidates are identified by source
commit, not asserted to be a published semantic release.

## Deployment acceptance checklist

A passing build is a release candidate, not production acceptance. Before a first
deployment, record an owner, environment URL, candidate commit, green CI URL,
change notes and rollback decision.

1. Provision PostgreSQL and separate runtime/migration accounts as described in
   database-setup.md. Take a backup and prove restoration in a disposable database.
   Rehearse all seven current migrations and Hibernate validation on the target
   PostgreSQL major version. Applied migration files must never be edited.
2. Configure the backend runtime using deploy/backend.env.example as a list of
   required settings. Inject the real values through the deployment secret store.
   Use JDK 25 or later and run java -jar backend/application.jar. Bootstrap the
   first administrator once using the identity guide, then disable bootstrap.
3. Register the public OIDC client with the exact HTTPS origin and
   /auth/callback redirect. Configure matching access-token audience/scopes,
   provider metadata/JWKS, and provision local profiles and organizations.
   Rebuild the frontend with the target public variables; confirm the manifest.
4. Serve frontend/ as static files through HTTPS with SPA fallback for routes such
   as /auth/callback. Proxy /api to the backend while preserving the Authorization
   header and /api path. API errors must not fall back to index.html. Do not cache
   authenticated API responses. Bind the backend/database to private interfaces.
   Do not expose Vite's development/preview server as the production web server.
5. Confirm actual provider login, callback, refresh/expiry handling and logout.
   Verify an unknown or disabled local user cannot access the API. Test two
   organizations with separate real accounts to confirm cross-organization denial.
6. In the target environment, exercise receipt -> request -> approval ->
   allocation -> shipment -> dispatch -> delivery. Confirm stock deducts once,
   delivery progress and audit attribution. Check cancellations, invalid quantities,
   conflict responses, both themes, keyboard focus and administrator-only screens.
7. Configure private application logs, alerting on availability/error rate/database
   failures, backup retention and an operational owner. There is no Actuator health
   endpoint implemented; a 401 from a protected API is only an HTTP/auth boundary
   smoke check and is not a complete ongoing dependency health check.
8. Record acceptance results and explicit deployment approval. Publish a version/tag
   and deploy through the chosen hosting process only after acceptance. This
   workflow never creates a public GitHub Release, tag or production deployment.

## Rollback

Keep the previous accepted backend/frontend pair, exact public build configuration,
runtime settings and verified database backup. Roll back both application artifacts
together only when the old version is compatible with the migrated schema.
Flyway does not automatically undo migrations. When a database rollback is needed,
stop writers, follow the rehearsed backup/restore process and account for writes
since the backup; do not drop schemas or run Flyway clean on a live environment.
Prefer a forward migration for recoverable schema problems. Verify inventory,
shipments, delivery progress and append-only audit history after recovery.

## Maintainer settings

For changes made through pull requests, configure a repository ruleset to require
the **CI gate** check, prevent force pushes, and require review as appropriate.
Workflow files alone do not enforce branch protection. These settings are not
enabled by this phase because repository administration is separate and the
current working agreement uses direct main commits.

Check the Actions page for the exact commit before accepting a release. Green
checks on an earlier commit do not validate a later change. Instructions and
scripts in this guide do not configure an external provider, hosting, backups,
repository variables, branch protection or production monitoring on your behalf.

## Reproduce locally

```sh
cd backend
./mvnw --batch-mode --no-transfer-progress -Ppostgres-it verify
cd ../frontend
npm ci
npm run format:check
npm audit --audit-level=high
npm test
npm run build
cd ..
python3 -m unittest discover -s scripts/tests -v
python3 scripts/smoke_backend.py
python3 scripts/package_release.py --commit <full-40-character-commit>
```

Set PG_TEST_URL/PG_TEST_USERNAME/PG_TEST_PASSWORD for a dedicated test database
before Maven, and DB_URL/DB_USERNAME/DB_PASSWORD for a disposable smoke database
before the packaged-JAR check. The smoke script listens on loopback port 18080,
applies production migrations and terminates its own process when finished.
Never point these test commands at production.
