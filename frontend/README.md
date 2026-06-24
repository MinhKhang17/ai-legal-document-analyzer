# LexiGuard AI Legal Suite Frontend

A production-oriented React + Vite + TypeScript + Tailwind CSS frontend for **LexiGuard AI Legal Suite**, a legal AI SaaS product. The implementation converts the screen exports into reusable React components, API-backed routed pages, responsive layouts, theme handling, and language switching.

## Tech stack

- React 18
- Vite
- TypeScript
- Tailwind CSS with local configuration
- React Router
- Lucide React icons
- React Context store for global UI state

No Bootstrap, jQuery, Material Symbols CDN, or external CSS framework is used.

## Install

```bash
npm install
```

## Run development server

```bash
npm run dev
```

The dev server defaults to `http://localhost:5173`.

## Build

```bash
npm run build
```

This runs TypeScript project build checks and then Vite production bundling.

## Preview production build

```bash
npm run preview
```

## Run with Docker

```bash
docker build -t lexiguard-frontend .
docker run --rm -p 8080:80 lexiguard-frontend
```

Then open `http://localhost:8080`.

## Folder structure

```text
Frontend/
├── package.json
├── vite.config.ts
├── tsconfig.json
├── tsconfig.app.json
├── tsconfig.node.json
├── tailwind.config.ts
├── postcss.config.cjs
├── .env.example
├── Dockerfile
├── README.md
├── public/
└── src/
    ├── api/
    ├── assets/
    ├── components/
    │   ├── common/
    │   ├── upload/
    │   ├── editor/
    │   ├── billing/
    │   └── admin/
    ├── hooks/
    ├── layouts/
    ├── pages/
    │   ├── auth/
    │   ├── dashboard/
    │   ├── projects/
    │   ├── jobs/
    │   ├── documents/
    │   ├── upload/
    │   ├── editor/
    │   ├── chat/
    │   ├── reports/
    │   ├── billing/
    │   ├── templates/
    │   ├── knowledge-base/
    │   └── admin/
    ├── routes/
    ├── store/
    ├── styles/
    ├── types/
    ├── utils/
    ├── App.tsx
    └── main.tsx
```

## Implemented routes

- `/login`
- `/register`
- `/dashboard`
- `/projects`
- `/projects/:id`
- `/documents`
- `/documents/:id`
- `/upload`
- `/editor`
- `/editor/risk-review`
- `/editor/version-comparison`
- `/editor/comparison-history`
- `/comparison-history`
- `/chat`
- `/chat/history`
- `/reports`
- `/reports/:id`
- `/knowledge-base`
- `/knowledge-base/:id`
- `/billing`
- `/jobs`
- `/templates`
- `/admin`
- `/admin/audit-logs`
- `/admin/system-health`

## Feature overview

The app reconstructs the major exported LexiGuard screens as a cohesive SPA:

- Login and workspace setup/auth pages
- API-backed customer dashboard, workspace portfolio, project detail, upload, chat, chat history, and billing flows
- API-backed admin console for users, payments, subscription plans, and legal tickets
- Direct AI-service pages for knowledge/risk/system health with unavailable states when AI-service is down
- Document, report, audit log, job, template, comparison, and detail routes that show empty/unavailable states until real backend APIs exist
- Role-aware navigation and route guards for ADMIN and CUSTOMER

## Design system

The Tailwind theme is based on the exported design tokens and product direction:

- Deep navy and primary blue for authority and action hierarchy
- Warm ivory canvas and white paper-like cards
- Gold accents for premium AI insights and legal intelligence highlights
- Refined risk colors for critical/high/medium/low legal risk
- Inter-oriented UI font stack and Domine-oriented legal heading font stack
- Dark mode class strategy with slate surfaces and desaturated gold accents
- Paper-card, AI-insight-card, document-paper, and form-field component classes

The implementation uses semantic components instead of pasted HTML. Shared components include:

- `AppShell`, `Sidebar`, `Topbar`, `PageHeader`
- `Button`, `Card`, `Badge`, `Tabs`, `Modal`, `Dropdown`, `DataTable`, `EmptyState`
- `StatCard`, `RiskBadge`, `StatusBadge`, `ThemeToggle`, `LanguageToggle`
- `FileUploadZone`, `ProcessingTimeline`
- `DocumentPreview`, `LegalChatPanel`, `RiskReviewPanel`, `VersionComparisonView`
- `AdminMetricCard`, `SystemHealthCard`, `BillingUsageCard`

## Theme switching

Theme state is implemented in `src/store/AppStore.tsx` and persisted to localStorage.

Supported modes:

- Light
- Dark
- System

System mode follows `prefers-color-scheme`. The store applies the `dark` class to `document.documentElement` when appropriate.

## Language switching

Language state is implemented in `src/store/AppStore.tsx` and persisted to localStorage.

Supported languages:

- English (`en`)
- Vietnamese (`vi`)

Translations live in `src/utils/i18n.ts`, and the `useI18n` hook exposes `t(key)` for labels, page titles, buttons, table headers, statuses, navigation items, and major UI text.

## Data policy

Production routes must not display fabricated domain data as if it came from the system.

- Backend endpoints are configured through `.env`, `.env.example`, and `src/config/api.ts`.
- Customer workspace, document upload, chat, billing, and admin tables use real backend services where endpoints exist.
- Direct AI-service pages use `VITE_AI_SERVICE_BASE_URL` and show unavailable states if the service is not reachable.
- Routes without a real backend/API contract render empty or unavailable states instead of fabricated documents, reports, jobs, audit logs, templates, comparison rows, or findings.

## Accessibility and responsive behavior

- Sidebar supports desktop collapse and mobile drawer behavior.
- Interactive controls include labels or aria-labels where appropriate.
- Tables use semantic table markup.
- Dialogs use `role="dialog"`, `aria-modal`, and Escape key close behavior.
- Forms use persistent labels.
- Layouts adapt across desktop, tablet, and mobile breakpoints.

## Verification

The codebase was checked with:

```bash
npm install
npm run build
```

The production build completed successfully.
