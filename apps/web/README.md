# Nexus Fleet & Personal Portfolio Web Workspace

This workspace (`apps/web`) builds **two strictly separated frontend bundles** served by `services/core-hub` (`personal-site-rust`):

1. **Personal Portfolio (`index.html` → `dist/`)** — Public site (`faizal.uk`), built from [`src/portfolio/`](src/portfolio/App.jsx).
2. **Cluster & Fleet Admin Console (`admin.html` → `dist-admin/`)** — Nexus Fleet control plane & CMS, built from [`src/admin/`](src/admin/Admin.jsx).

## Source Structure

```text
src/
├── portfolio/   # Public site (App.jsx, main.jsx, pages, Loader, context, utils)
├── admin/       # Nexus Fleet & Cluster Admin Console (main.jsx, Admin.jsx, tabs, components)
├── shared/      # Shared UI primitives (PasscodeModal.jsx)
└── assets/      # Shared fonts, SVGs, and static images
```

The portfolio's 5-click monogram trigger opens [`PasscodeModal`](src/shared/PasscodeModal.jsx) and redirects to `/admin.html` on unlock—keeping all admin components and tabs out of the public `dist/` bundle.

## Development & Build Scripts

All dev servers proxy `/api`, `/save-message`, and `/uploads` to the local Rust backend (`http://127.0.0.1:3002`).

- `npm run dev` / `npm run dev:portfolio` — Start portfolio dev server (also serves `/admin` and `/admin.html` on port `5173`).
- `npm run dev:admin` — Start standalone admin console dev server on port `5174` (opens `/admin.html`).
- `npm run build` / `npm run build:portfolio` — Build public portfolio bundle to `dist/`.
- `npm run build:admin` — Build standalone admin bundle to `dist-admin/`.
- `npm run build:all` — Build both `dist/` and `dist-admin/`.
- `npm run lint` — Run `oxlint`.
