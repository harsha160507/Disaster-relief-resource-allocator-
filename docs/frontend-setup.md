# Phase 5 frontend

The desktop workspace uses React, TypeScript and Vite. It includes an inventory-manager
overview, inventory/catalogue/location management, request submission and approval,
automatic and exact-quantity allocations, shipment workflows, organization/user
administration and restricted audit history.

The text logo retains **Disaster Relief Resource Allocator**. Light and dark themes
use blue accents (#2563EB and #60A5FA respectively). A supplied switch changes the
theme and saves it locally; the device preference is used on the first visit.

## Run locally

Verified with Node 24.22.1. Use Node 24 or another version supported by the pinned
Vite/Vitest releases. Install frontend dependencies from the lockfile:

```sh
cd frontend
npm ci
npm run dev
```

Open the printed local URL (normally http://127.0.0.1:5173). The development server
proxies /api to the backend at http://127.0.0.1:8080. Start that backend using the
database/identity configuration from earlier phases. The frontend adds no database
migration and retains the backend's authentication and organization boundaries.

```sh
npm test
npm run build
npm run format:check
```

The production bundle is frontend/dist. Host it with SPA fallback to index.html,
including the /auth/callback route. Serve /api from the Spring backend through
the same-origin reverse proxy; do not route API errors through the SPA fallback.
Supply HTTPS, preserve Authorization headers and disable caching of authenticated
API responses. Do not run the development server as a production server.

## Organization sign-in

Copy frontend/.env.example to an untracked frontend/.env.local and configure:

- VITE_OIDC_AUTHORITY: issuer/authority URL.
- VITE_OIDC_CLIENT_ID: public SPA client identifier.
- VITE_OIDC_SCOPE: scopes configured by the identity provider.
- VITE_OIDC_AUDIENCE: optional provider-specific audience parameter.

Register a public browser client using authorization code with PKCE. Allow the
exact frontend origin as the client's web origin and register /auth/callback as
its redirect URL. The backend still checks signature, issuer, audience, expiration,
local profile provisioning, active organization and saved roles. A token's role
claims do not grant permissions.

The provider must issue a JWT access token accepted by this API. Configure its
API audience/scopes and the backend's matching issuer/audience. No external provider
account is created by this phase. Without this public configuration, organization
sign-in is unavailable and the screen explains how to enable it.

VITE_ configuration is public build-time data. Never add client secrets,
passwords or signing keys. The frontend does not collect passwords. Access-token
state is kept in session storage by the OIDC library; the theme preference is the
only application setting stored in local storage.

VITE_API_BASE_URL can override the API origin when a deployment separately supplies
the required CORS configuration. The supported default is a same-origin /api proxy.
Changing Vite variables requires rebuilding the production bundle.

## Explicit demo workspace

Explore demo workspace enters an in-memory sample environment. Demo requests never
reach the live backend; failures in a live session never silently switch to demo.
The workspace remains labelled as a demo, including the sample role selector.
Changes reset on reload. Roles selected here are visual previews, not real
permissions or proof of authentication.

Demo records illustrate receipts, exact/automatic reservations, shipment creation,
dispatch, delivery and cancellation. Screenshots of this mode demonstrate the
actual UI with sample records, not a production connection.

## Workflow guidance

- Inventory managers receive/adjust stock and allocate approved requests.
- Field agents/partner managers create their own requests; administrators can
  select a verified requester. Drafts are saved first, then resources are added
  and the request is submitted. The backend verifies affected destinations.
- Coordinators review requests. Inventory managers and coordinators can allocate
  automatically; inventory managers can also reserve exact quantities.
- Shipments group unassigned reserved allocations for one destination. Dispatch
  deducts stock once; delivery tracks receipt. Cancellation is before dispatch.
- FULFILLED is shown as Fully allocated, independently of actual delivery.
- Organization and user edits preserve version values; conflicts require reloading.
- Audit history is administrator-only. Every live mutation uses the existing
  audited business endpoints.

Server pagination/search/sorting are used for workspace tables. Searchable record
selectors query the server rather than loading an entire catalogue. Error, empty,
loading, disabled, focus and confirmation states are included. Modal dialogs use
the native dialog element for focus containment and return focus on close.
Tables render data as React text and actions capture the record object, preventing
the original supplied-table row-index mismatch after sorting/filtering.
