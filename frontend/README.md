# LexiGuard AI Legal Suite Frontend

A production-oriented React + Vite + TypeScript + Tailwind CSS rebuild of the exported Stitch UI screens for **LexiGuard AI Legal Suite**, a legal AI SaaS product. The implementation converts the screen exports into reusable React components, mock data, routed pages, responsive layouts, theme handling, and language switching.

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
- Overview dashboard with metrics, risk trend, AI insight, queue, and review table
- Project portfolio and project detail workspace
- Document management and document detail preview
- Upload processing flow with drag-and-drop and fake processing state
- AI legal risk review with document preview, findings, citations, and processing timeline
- Legal chat and chat history with mock AI response behavior
- Version comparison and comparison history
- Reports generation and report detail/preview page
- Knowledge base management and knowledge source detail page
- Billing and usage page
- Admin console, audit logs, and system health/model status page
- Jobs and templates pages for scalable workspace coverage

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
- `DocumentPreview`, `LegalChatPanel`, `RiskReviewPanel`, `VersionComparisonView`, `ReportPreview`
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

## Mock data and mock API

Mock domain data is stored in `src/api/mockData.ts`:

- projects
- documents
- risk findings
- reports
- chat threads/messages
- audit logs
- workspace users
- billing usage and invoices
- system services and processing jobs
- knowledge base articles
- comparison history rows

`src/api/mockApi.ts` provides delayed mock API methods. Pages currently import mock data directly for clarity, but the mock API module is ready for replacing direct imports with asynchronous calls.

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
