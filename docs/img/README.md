# README screenshot tour

These PNGs show the actual frontend's **in-memory demo workspace**, captured at a 1440 × 1120 desktop viewport. They contain sample records, not live relief data. Full-page captures may be taller than the viewport.

The [README](../../README.md#guided-tour) follows request `REQ-2026-1046`: approve 100 first aid kits, allocate stock, prepare `SHP-TOUR-001`, dispatch, and confirm delivery. The capture script checks the visible request states and final stock balance (540 on hand, 180 reserved, 360 available), then captures the dark overview. It fails on browser errors or an unexpected result.

## Recreate the pictures

Use Node.js 24. From the repository root:

```sh
cd frontend
npm ci
npm install --no-save --package-lock=false playwright
npx playwright install chromium
npm run dev
```

Leave the frontend running. In another terminal, from the repository root:

```sh
node scripts/capture_readme_tour.cjs
```

The script opens a fresh browser session and rewrites `docs/img/tour/*.png`. It exercises only the demo; no backend, database or identity provider is required. Demo records reset on reload.

Set `BASE_URL` if the frontend is served elsewhere, and optionally `CHROME_PATH` to use an existing Chrome executable. For example, in PowerShell:

```powershell
$env:BASE_URL = "http://127.0.0.1:4173"
$env:CHROME_PATH = "C:\Program Files\Google\Chrome\Application\chrome.exe"
node scripts/capture_readme_tour.cjs
```

Screenshots capture real UI states. Dates and generated record IDs can differ between runs. This walkthrough checks demo behavior; it does not verify live identity, backend permissions or PostgreSQL transactions.

Keep image filenames and README descriptions in sync when the interface changes.
