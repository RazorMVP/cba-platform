# React Migration — Phase 0: Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Scaffold `web-react/` with Vite + React + TypeScript, wire Tailwind CSS v4 with Nubeero OKLCH design tokens, load Epilogue + Geist fonts, initialise shadcn/ui, implement the app shell (Shell + Sidebar + Topbar), shared components (StatusBadge, DataTable, KpiCard, PageHeader, Modal), Axios API client, AuthContext bypass, full React Router v6 route tree pointing at placeholders, and a Vercel config + CI job — producing a running app where navigation works end-to-end but all feature pages say "Coming soon."

**Architecture:** Vite SPA; React Router v6 lazy routes; TanStack Query v5 for data; Axios for HTTP; shadcn/ui copy-paste components styled with Tailwind CSS v4 CSS-first config (`@theme` in globals.css — no `tailwind.config.ts`). Auth bypass via `VITE_AUTH_BYPASS=true` env var during development. Dark shell (oklch near-black) + white content cards.

**Tech Stack:** React 19, Vite 6, TypeScript 5 strict, React Router v6, TanStack Query v5, Axios, Tailwind CSS v4 (`@tailwindcss/vite`), shadcn/ui, Vitest + React Testing Library, Geist npm package, Epilogue via Google Fonts.

---

## File Map

| File | Responsibility |
|------|---------------|
| `web-react/package.json` | All deps — React, Router, TanStack Query, Axios, Tailwind, shadcn, Vitest, Testing Library |
| `web-react/vite.config.ts` | Vite + `@tailwindcss/vite` plugin + `@/` path alias + Vitest inline config |
| `web-react/tsconfig.json` | Strict mode + `@/` path alias |
| `web-react/tsconfig.node.json` | Node config for vite.config.ts |
| `web-react/index.html` | Epilogue Google Fonts link + app mount point |
| `web-react/components.json` | shadcn/ui config (`@/` alias, Tailwind CSS vars) |
| `web-react/vercel.json` | SPA rewrite rule + asset cache headers |
| `web-react/src/styles/globals.css` | `@import "tailwindcss"` + `@theme` OKLCH tokens + font-face rules + base resets |
| `web-react/src/main.tsx` | React root; QueryClientProvider + RouterProvider |
| `web-react/src/app/router.tsx` | All 57 routes (lazy) + placeholder fallback |
| `web-react/src/app/layout/Shell.tsx` | App frame: fixed sidebar + topbar + scrollable main `<Outlet />` |
| `web-react/src/app/layout/Sidebar.tsx` | Dark nav with section groups, active state, Epilogue logo |
| `web-react/src/app/layout/Topbar.tsx` | Page title + user avatar |
| `web-react/src/app/features/placeholder/PlaceholderPage.tsx` | "Coming soon" page used by all unbuilt routes |
| `web-react/src/core/api/apiClient.ts` | Axios instance: base URL from `VITE_API_URL`, auth header interceptor |
| `web-react/src/core/auth/AuthContext.tsx` | Context + provider: bypass flag injects ADMIN/TELLER/CUSTOMER roles |
| `web-react/src/shared/components/StatusBadge.tsx` | Variant badges: success/warning/error/info/neutral/primary |
| `web-react/src/shared/components/DataTable.tsx` | Generic typed table: columns config + data array + empty state |
| `web-react/src/shared/components/KpiCard.tsx` | Metric card: label + value + optional delta |
| `web-react/src/shared/components/PageHeader.tsx` | Page title + optional actions slot |
| `web-react/src/shared/components/Modal.tsx` | Accessible dialog: title + children + footer actions |
| `web-react/src/shared/components/cn.ts` | `clsx` + `tailwind-merge` utility |
| `web-react/src/shared/components/StatusBadge.test.tsx` | Vitest unit tests |
| `web-react/src/shared/components/DataTable.test.tsx` | Vitest unit tests |
| `web-react/src/core/auth/AuthContext.test.tsx` | Vitest unit tests |
| `web-react/src/core/api/apiClient.test.ts` | Vitest unit tests |
| `.github/workflows/web-ci.yml` | Add `react-deploy` job (additive — Angular job untouched) |

---

## Task 1: Scaffold the project

**Files:**
- Create: `web-react/package.json`
- Create: `web-react/vite.config.ts`
- Create: `web-react/tsconfig.json`
- Create: `web-react/tsconfig.node.json`
- Create: `web-react/index.html`

- [ ] **Step 1: Create the Vite + React + TypeScript project**

```bash
cd /Users/razormvp/CoreBanking
npm create vite@latest web-react -- --template react-ts
cd web-react
```

- [ ] **Step 2: Install all dependencies**

```bash
npm install \
  react-router-dom@6 \
  @tanstack/react-query@5 \
  axios \
  clsx \
  tailwind-merge \
  geist

npm install -D \
  @tailwindcss/vite \
  tailwindcss \
  vitest \
  @vitest/ui \
  @testing-library/react \
  @testing-library/user-event \
  @testing-library/jest-dom \
  jsdom \
  @types/node
```

- [ ] **Step 3: Replace `vite.config.ts` entirely**

```typescript
// web-react/vite.config.ts
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { resolve } from 'path'

export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, './src'),
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test-setup.ts'],
    css: true,
  },
})
```

- [ ] **Step 4: Replace `tsconfig.json` entirely**

```json
{
  "files": [],
  "references": [
    { "path": "./tsconfig.app.json" },
    { "path": "./tsconfig.node.json" }
  ]
}
```

- [ ] **Step 5: Create `tsconfig.app.json`**

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "useDefineForClassFields": true,
    "lib": ["ES2022", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "isolatedModules": true,
    "moduleDetection": "force",
    "noEmit": true,
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,
    "baseUrl": ".",
    "paths": {
      "@/*": ["./src/*"]
    }
  },
  "include": ["src"]
}
```

- [ ] **Step 6: Create `tsconfig.node.json`**

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "lib": ["ES2022"],
    "module": "ESNext",
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "isolatedModules": true,
    "moduleDetection": "force",
    "noEmit": true,
    "strict": true
  },
  "include": ["vite.config.ts"]
}
```

- [ ] **Step 7: Create Vitest setup file**

```typescript
// web-react/src/test-setup.ts
import '@testing-library/jest-dom'
```

- [ ] **Step 8: Verify the scaffold compiles**

```bash
cd /Users/razormvp/CoreBanking/web-react
npm run build
```

Expected: Build succeeds with no TypeScript errors.

- [ ] **Step 9: Commit**

```bash
git add web-react/
git commit -m "feat(web-react): scaffold Vite + React + TS + Tailwind v4 + Vitest"
```

---

## Task 2: Design token system (globals.css)

**Files:**
- Create: `web-react/src/styles/globals.css`
- Modify: `web-react/index.html`

- [ ] **Step 1: Replace `index.html` with font links + correct mount point**

```html
<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>CBA Backoffice</title>
    <link rel="preconnect" href="https://fonts.googleapis.com" />
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />
    <link
      href="https://fonts.googleapis.com/css2?family=Epilogue:wght@500;600;700&display=swap"
      rel="stylesheet"
    />
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

- [ ] **Step 2: Create `globals.css` with Tailwind import, Geist font-face, OKLCH tokens, and base resets**

```css
/* web-react/src/styles/globals.css */

/* ── Tailwind CSS v4 (CSS-first config) ─────────────────────── */
@import "tailwindcss";

/* ── Geist font (npm package) ───────────────────────────────── */
@font-face {
  font-family: "Geist";
  src: url("../../../node_modules/geist/dist/fonts/geist-sans/Geist-Regular.woff2") format("woff2");
  font-weight: 400;
  font-display: swap;
}
@font-face {
  font-family: "Geist";
  src: url("../../../node_modules/geist/dist/fonts/geist-sans/Geist-Medium.woff2") format("woff2");
  font-weight: 500;
  font-display: swap;
}

/* ── OKLCH Design Tokens ────────────────────────────────────── */
:root {
  /* Surfaces */
  --bg-app:     oklch(10% 0.018 250);
  --bg-sidebar: oklch(15% 0.028 250);
  --bg-card:    oklch(99% 0.004 250);
  --bg-subtle:  oklch(96% 0.006 250);

  /* Brand */
  --color-primary: oklch(28% 0.045 250);
  --color-accent:  oklch(72% 0.13 68);   /* warm amber — focus, active, key status ONLY */

  /* Text */
  --color-text:    oklch(8% 0.020 250);
  --color-muted:   oklch(55% 0.010 250);

  /* Border */
  --color-border:  oklch(88% 0.008 250);

  /* Semantic */
  --color-success: oklch(48% 0.15 145);
  --color-warning: oklch(65% 0.14 70);
  --color-error:   oklch(50% 0.18 25);
  --color-info:    oklch(55% 0.14 250);

  --color-success-bg: oklch(96% 0.04 145);
  --color-warning-bg: oklch(97% 0.04 70);
  --color-error-bg:   oklch(97% 0.04 25);
  --color-info-bg:    oklch(96% 0.04 250);

  /* Spacing (4pt scale) */
  --space-1:  4px;
  --space-2:  8px;
  --space-3:  12px;
  --space-4:  16px;
  --space-6:  24px;
  --space-8:  32px;
  --space-12: 48px;
  --space-16: 64px;

  /* Layout */
  --sidebar-width: 256px;
  --topbar-height: 60px;

  /* Radius */
  --radius-sm:   6px;
  --radius-md:   10px;
  --radius-lg:   14px;
  --radius-full: 9999px;

  /* Easing */
  --ease-out-quint: cubic-bezier(0.16, 1, 0.3, 1);
}

/* ── Tailwind theme extension ───────────────────────────────── */
@theme {
  --font-display: "Epilogue", system-ui, sans-serif;
  --font-sans:    "Geist", system-ui, sans-serif;

  --color-bg-app:     var(--bg-app);
  --color-bg-sidebar: var(--bg-sidebar);
  --color-bg-card:    var(--bg-card);
  --color-bg-subtle:  var(--bg-subtle);
  --color-primary:    var(--color-primary);
  --color-accent:     var(--color-accent);
  --color-text:       var(--color-text);
  --color-muted:      var(--color-muted);
  --color-border:     var(--color-border);
  --color-success:    var(--color-success);
  --color-warning:    var(--color-warning);
  --color-error:      var(--color-error);
  --color-info:       var(--color-info);
}

/* ── Base resets ─────────────────────────────────────────────── */
*, *::before, *::after { box-sizing: border-box; }

html, body, #root {
  height: 100%;
  margin: 0;
  padding: 0;
}

body {
  font-family: var(--font-sans);
  background: var(--bg-app);
  color: var(--color-text);
  -webkit-font-smoothing: antialiased;
}

/* Tabular numerals everywhere financial data appears */
table, .tabular {
  font-variant-numeric: tabular-nums;
}

/* Reduced motion */
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 0.01ms !important;
    transition-duration: 0.01ms !important;
  }
}
```

- [ ] **Step 3: Update `main.tsx` to import globals.css**

```tsx
// web-react/src/main.tsx
import '@/styles/globals.css'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from 'react-router-dom'
import { router } from '@/app/router'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60 * 5,   // 5 minutes
      retry: 1,
    },
  },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </StrictMode>,
)
```

- [ ] **Step 4: Verify build still passes**

```bash
cd /Users/razormvp/CoreBanking/web-react
npm run build
```

Expected: Build succeeds.

- [ ] **Step 5: Commit**

```bash
git add web-react/index.html web-react/src/styles/globals.css web-react/src/main.tsx
git commit -m "feat(web-react): OKLCH design tokens, Epilogue + Geist fonts, Tailwind v4 theme"
```

---

## Task 3: `cn` utility + shadcn/ui init

**Files:**
- Create: `web-react/src/shared/components/cn.ts`
- Create: `web-react/components.json`

- [ ] **Step 1: Create the `cn` utility**

```typescript
// web-react/src/shared/components/cn.ts
import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs))
}
```

- [ ] **Step 2: Create `components.json` for shadcn/ui**

```json
{
  "$schema": "https://ui.shadcn.com/schema.json",
  "style": "default",
  "rsc": false,
  "tsx": true,
  "tailwind": {
    "config": "",
    "css": "src/styles/globals.css",
    "baseColor": "slate",
    "cssVariables": true,
    "prefix": ""
  },
  "aliases": {
    "components": "@/shared/components",
    "utils": "@/shared/components/cn",
    "ui": "@/shared/components/ui",
    "lib": "@/shared/lib",
    "hooks": "@/shared/hooks"
  }
}
```

- [ ] **Step 3: Write the `cn` unit test**

```typescript
// web-react/src/shared/components/cn.test.ts
import { describe, it, expect } from 'vitest'
import { cn } from './cn'

describe('cn', () => {
  it('merges class names', () => {
    expect(cn('px-4', 'py-2')).toBe('px-4 py-2')
  })

  it('resolves Tailwind conflicts in favour of the last class', () => {
    expect(cn('px-2', 'px-4')).toBe('px-4')
  })

  it('handles conditional falsy values', () => {
    expect(cn('base', false && 'never', undefined, 'end')).toBe('base end')
  })
})
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd /Users/razormvp/CoreBanking/web-react
npx vitest run src/shared/components/cn.test.ts
```

Expected: 3 tests pass.

- [ ] **Step 5: Commit**

```bash
git add web-react/src/shared/components/cn.ts web-react/src/shared/components/cn.test.ts web-react/components.json
git commit -m "feat(web-react): cn utility + shadcn/ui config"
```

---

## Task 4: AuthContext

**Files:**
- Create: `web-react/src/core/auth/AuthContext.tsx`
- Create: `web-react/src/core/auth/AuthContext.test.tsx`

- [ ] **Step 1: Write the failing test**

```tsx
// web-react/src/core/auth/AuthContext.test.tsx
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { AuthProvider, useAuth } from './AuthContext'

function RoleDisplay() {
  const { roles, isAdmin, isTeller } = useAuth()
  return (
    <div>
      <span data-testid="roles">{roles.join(',')}</span>
      <span data-testid="isAdmin">{String(isAdmin)}</span>
      <span data-testid="isTeller">{String(isTeller)}</span>
    </div>
  )
}

describe('AuthContext — bypass mode', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_AUTH_BYPASS', 'true')
  })

  it('injects ADMIN, TELLER, CUSTOMER roles when bypass is true', () => {
    render(<AuthProvider><RoleDisplay /></AuthProvider>)
    expect(screen.getByTestId('roles').textContent).toBe('ADMIN,TELLER,CUSTOMER')
    expect(screen.getByTestId('isAdmin').textContent).toBe('true')
    expect(screen.getByTestId('isTeller').textContent).toBe('true')
  })
})

describe('AuthContext — production mode', () => {
  beforeEach(() => {
    vi.stubEnv('VITE_AUTH_BYPASS', 'false')
  })

  it('has no roles when bypass is false and no token provided', () => {
    render(<AuthProvider><RoleDisplay /></AuthProvider>)
    expect(screen.getByTestId('roles').textContent).toBe('')
    expect(screen.getByTestId('isAdmin').textContent).toBe('false')
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd /Users/razormvp/CoreBanking/web-react
npx vitest run src/core/auth/AuthContext.test.tsx
```

Expected: FAIL — "Cannot find module './AuthContext'"

- [ ] **Step 3: Implement `AuthContext.tsx`**

```tsx
// web-react/src/core/auth/AuthContext.tsx
import { createContext, useContext, useMemo, type ReactNode } from 'react'

export type Role = 'ADMIN' | 'TELLER' | 'CUSTOMER'

interface AuthState {
  roles: Role[]
  isAdmin: boolean
  isTeller: boolean
  isCustomer: boolean
  token: string | null
}

const AuthContext = createContext<AuthState | null>(null)

const BYPASS_ROLES: Role[] = ['ADMIN', 'TELLER', 'CUSTOMER']

export function AuthProvider({ children }: { children: ReactNode }) {
  const isBypass = import.meta.env.VITE_AUTH_BYPASS === 'true'

  const value = useMemo<AuthState>(() => {
    if (isBypass) {
      return {
        roles: BYPASS_ROLES,
        isAdmin: true,
        isTeller: true,
        isCustomer: true,
        token: 'dev-bypass-token',
      }
    }
    // Production: token would be read from Keycloak session
    // Placeholder until Keycloak is wired at cutover
    return {
      roles: [],
      isAdmin: false,
      isTeller: false,
      isCustomer: false,
      token: null,
    }
  }, [isBypass])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd /Users/razormvp/CoreBanking/web-react
npx vitest run src/core/auth/AuthContext.test.tsx
```

Expected: 2 tests pass.

- [ ] **Step 5: Add `AuthProvider` to `main.tsx`**

```tsx
// web-react/src/main.tsx  (full replacement)
import '@/styles/globals.css'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from 'react-router-dom'
import { router } from '@/app/router'
import { AuthProvider } from '@/core/auth/AuthContext'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 1000 * 60 * 5, retry: 1 },
  },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </QueryClientProvider>
  </StrictMode>,
)
```

- [ ] **Step 6: Commit**

```bash
git add web-react/src/core/auth/
git commit -m "feat(web-react): AuthContext with dev bypass and role helpers"
```

---

## Task 5: API client

**Files:**
- Create: `web-react/src/core/api/apiClient.ts`
- Create: `web-react/src/core/api/apiClient.test.ts`
- Create: `web-react/.env.local` (gitignored)

- [ ] **Step 1: Create `.env.local` for local dev**

```bash
cat > /Users/razormvp/CoreBanking/web-react/.env.local << 'EOF'
VITE_API_URL=http://localhost:8080/api/v1
VITE_AUTH_BYPASS=true
EOF
```

- [ ] **Step 2: Add `.env.local` to the project's `.gitignore`**

```bash
cat > /Users/razormvp/CoreBanking/web-react/.gitignore << 'EOF'
node_modules
dist
.env.local
.env.*.local
*.local
EOF
```

- [ ] **Step 3: Write the failing test**

```typescript
// web-react/src/core/api/apiClient.test.ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import axios from 'axios'

// Mock environment variable
vi.stubEnv('VITE_API_URL', 'http://test-api.local/api/v1')
vi.stubEnv('VITE_AUTH_BYPASS', 'true')

describe('apiClient', () => {
  it('has the correct base URL', async () => {
    const { apiClient } = await import('./apiClient')
    expect(apiClient.defaults.baseURL).toBe('http://test-api.local/api/v1')
  })

  it('attaches Authorization header in bypass mode', async () => {
    const { apiClient } = await import('./apiClient')
    // Trigger the request interceptor by inspecting its config
    const interceptorConfig = await new Promise<Record<string, unknown>>(resolve => {
      const id = apiClient.interceptors.request.use(config => {
        resolve(config.headers as Record<string, unknown>)
        apiClient.interceptors.request.eject(id)
        return config
      })
      // Fire a request (will fail network but interceptor runs first)
      apiClient.get('/test').catch(() => {})
    })
    expect(interceptorConfig['Authorization']).toBe('Bearer dev-bypass-token')
  })
})
```

- [ ] **Step 4: Run the test to verify it fails**

```bash
cd /Users/razormvp/CoreBanking/web-react
npx vitest run src/core/api/apiClient.test.ts
```

Expected: FAIL — "Cannot find module './apiClient'"

- [ ] **Step 5: Implement `apiClient.ts`**

```typescript
// web-react/src/core/api/apiClient.ts
import axios from 'axios'

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api/v1',
  headers: { 'Content-Type': 'application/json' },
})

// Auth header interceptor
// In dev bypass mode: attaches a static dev token.
// At Keycloak cutover: replace with token from Keycloak adapter.
apiClient.interceptors.request.use(config => {
  const isBypass = import.meta.env.VITE_AUTH_BYPASS === 'true'
  const token = isBypass
    ? 'dev-bypass-token'
    : (window as Window & { __keycloakToken?: string }).__keycloakToken ?? null

  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`
  }
  return config
})

// Response error normaliser
apiClient.interceptors.response.use(
  response => response,
  error => {
    // Re-throw with a normalised structure so components can safely read error.message
    const message: string =
      error.response?.data?.errors?.[0]?.message ??
      error.response?.data?.message ??
      error.message ??
      'An unexpected error occurred'
    return Promise.reject(new Error(message))
  },
)
```

- [ ] **Step 6: Run the test to verify it passes**

```bash
cd /Users/razormvp/CoreBanking/web-react
npx vitest run src/core/api/apiClient.test.ts
```

Expected: 2 tests pass.

- [ ] **Step 7: Commit**

```bash
git add web-react/src/core/api/ web-react/.env.local web-react/.gitignore
git commit -m "feat(web-react): Axios apiClient with auth interceptor and error normaliser"
```

---

## Task 6: Placeholder page + React Router

**Files:**
- Create: `web-react/src/app/features/placeholder/PlaceholderPage.tsx`
- Create: `web-react/src/app/router.tsx`

- [ ] **Step 1: Create the placeholder page**

```tsx
// web-react/src/app/features/placeholder/PlaceholderPage.tsx
import { useLocation } from 'react-router-dom'

export default function PlaceholderPage() {
  const { pathname } = useLocation()
  return (
    <div className="flex flex-col gap-3 p-8">
      <p className="font-display text-xl font-semibold text-[var(--color-text)]">
        Coming soon
      </p>
      <p className="text-sm text-[var(--color-muted)]">{pathname}</p>
    </div>
  )
}
```

- [ ] **Step 2: Create the full route tree**

```tsx
// web-react/src/app/router.tsx
import { createBrowserRouter, Navigate } from 'react-router-dom'
import { lazy, Suspense } from 'react'
import Shell from '@/app/layout/Shell'

const Placeholder = lazy(() => import('@/app/features/placeholder/PlaceholderPage'))

function Page() {
  return (
    <Suspense fallback={<div className="p-8 text-sm text-[var(--color-muted)]">Loading…</div>}>
      <Placeholder />
    </Suspense>
  )
}

export const router = createBrowserRouter([
  {
    path: '/',
    element: <Shell />,
    children: [
      { index: true, element: <Navigate to="/dashboard" replace /> },

      // Operations
      { path: 'dashboard',                      element: <Page /> },
      { path: 'customers',                       element: <Page /> },
      { path: 'customers/:id',                   element: <Page /> },
      { path: 'accounts',                        element: <Page /> },
      { path: 'accounts/:id',                    element: <Page /> },
      { path: 'payments',                        element: <Page /> },
      { path: 'payments/:id',                    element: <Page /> },
      { path: 'loans',                           element: <Page /> },
      { path: 'loans/:id',                       element: <Page /> },
      { path: 'tellers',                         element: <Page /> },
      { path: 'tellers/:id',                     element: <Page /> },

      // Products
      { path: 'products/loan-products',          element: <Page /> },
      { path: 'products/loan-products/:id',      element: <Page /> },
      { path: 'products/deposit-products',       element: <Page /> },
      { path: 'products/deposit-products/:id',   element: <Page /> },
      { path: 'products/fixed-deposits',         element: <Page /> },
      { path: 'products/fixed-deposits/:id',     element: <Page /> },
      { path: 'products/recurring-deposits',     element: <Page /> },
      { path: 'products/recurring-deposits/:id', element: <Page /> },
      { path: 'products/shares',                 element: <Page /> },
      { path: 'products/shares/:id',             element: <Page /> },

      // Accounting
      { path: 'accounting/gl-accounts',          element: <Page /> },
      { path: 'accounting/journal-entries',      element: <Page /> },
      { path: 'accounting/provisioning',         element: <Page /> },
      { path: 'accounting/financial-activities', element: <Page /> },

      // Cards
      { path: 'cards',                           element: <Page /> },
      { path: 'cards/:id',                       element: <Page /> },
      { path: 'cards/products',                  element: <Page /> },
      { path: 'cards/fraud',                     element: <Page /> },
      { path: 'cards/settlement',                element: <Page /> },
      { path: 'cards/disputes',                  element: <Page /> },
      { path: 'cards/terminal',                  element: <Page /> },
      { path: 'cards/api-keys',                  element: <Page /> },
      { path: 'cards/webhooks',                  element: <Page /> },
      { path: 'cards/bins',                      element: <Page /> },
      { path: 'cards/schemes',                   element: <Page /> },
      { path: 'cards/interchange',               element: <Page /> },

      // Reports
      { path: 'reports',                         element: <Page /> },
      { path: 'reports/scheduler',               element: <Page /> },
      { path: 'reports/mailing',                 element: <Page /> },

      // Admin
      { path: 'admin/users',                     element: <Page /> },
      { path: 'admin/roles',                     element: <Page /> },
      { path: 'admin/offices',                   element: <Page /> },
      { path: 'admin/hooks',                     element: <Page /> },
      { path: 'admin/maker-checker',             element: <Page /> },
      { path: 'admin/notifications',             element: <Page /> },
      { path: 'admin/tpp',                       element: <Page /> },

      // Groups
      { path: 'groups',                          element: <Page /> },
      { path: 'groups/:id',                      element: <Page /> },
      { path: 'centers',                         element: <Page /> },
      { path: 'centers/:id',                     element: <Page /> },

      // System
      { path: 'system/codes',                    element: <Page /> },
      { path: 'system/config',                   element: <Page /> },
      { path: 'system/floating-rates',           element: <Page /> },
      { path: 'system/taxes',                    element: <Page /> },
      { path: 'system/account-algorithms',       element: <Page /> },

      // Open Banking
      { path: 'open-banking/consents',           element: <Page /> },
      { path: 'open-banking/consents/:id',       element: <Page /> },

      // Catch-all
      { path: '*',                               element: <Navigate to="/dashboard" replace /> },
    ],
  },
])
```

- [ ] **Step 3: Verify TypeScript is happy**

```bash
cd /Users/razormvp/CoreBanking/web-react
npx tsc --noEmit
```

Expected: No errors (Shell does not exist yet — create a stub in next task before running this).

- [ ] **Step 4: Commit**

```bash
git add web-react/src/app/
git commit -m "feat(web-react): React Router v6 with all 57 routes + placeholder page"
```

---

## Task 7: App Shell (Shell + Sidebar + Topbar)

**Files:**
- Create: `web-react/src/app/layout/Shell.tsx`
- Create: `web-react/src/app/layout/Sidebar.tsx`
- Create: `web-react/src/app/layout/Topbar.tsx`

- [ ] **Step 1: Create `Sidebar.tsx`**

```tsx
// web-react/src/app/layout/Sidebar.tsx
import { NavLink } from 'react-router-dom'
import { cn } from '@/shared/components/cn'

interface NavItem {
  label: string
  path: string
  exact?: boolean
}

interface NavSection {
  heading: string
  items: NavItem[]
}

const NAV: NavSection[] = [
  {
    heading: 'Operations',
    items: [
      { label: 'Dashboard',  path: '/dashboard', exact: true },
      { label: 'Customers',  path: '/customers' },
      { label: 'Accounts',   path: '/accounts' },
      { label: 'Loans',      path: '/loans' },
      { label: 'Payments',   path: '/payments' },
      { label: 'Tellers',    path: '/tellers' },
    ],
  },
  {
    heading: 'Products',
    items: [
      { label: 'Loan Products',       path: '/products/loan-products' },
      { label: 'Deposit Products',    path: '/products/deposit-products' },
      { label: 'Fixed Deposits',      path: '/products/fixed-deposits' },
      { label: 'Recurring Deposits',  path: '/products/recurring-deposits' },
      { label: 'Shares',              path: '/products/shares' },
    ],
  },
  {
    heading: 'Accounting',
    items: [
      { label: 'GL Accounts',          path: '/accounting/gl-accounts' },
      { label: 'Journal Entries',      path: '/accounting/journal-entries' },
      { label: 'Provisioning',         path: '/accounting/provisioning' },
      { label: 'Financial Activities', path: '/accounting/financial-activities' },
    ],
  },
  {
    heading: 'Cards',
    items: [
      { label: 'Card List',        path: '/cards', exact: true },
      { label: 'Card Products',    path: '/cards/products' },
      { label: 'Fraud Rules',      path: '/cards/fraud' },
      { label: 'Settlement',       path: '/cards/settlement' },
      { label: 'Disputes',         path: '/cards/disputes' },
      { label: 'Terminal',         path: '/cards/terminal' },
      { label: 'API Keys',         path: '/cards/api-keys' },
      { label: 'Webhooks',         path: '/cards/webhooks' },
      { label: 'BIN Management',   path: '/cards/bins' },
      { label: 'Schemes',          path: '/cards/schemes' },
      { label: 'Interchange',      path: '/cards/interchange' },
    ],
  },
  {
    heading: 'Reports',
    items: [
      { label: 'Reports',        path: '/reports', exact: true },
      { label: 'CoB Scheduler',  path: '/reports/scheduler' },
      { label: 'Mailing Jobs',   path: '/reports/mailing' },
    ],
  },
  {
    heading: 'Admin',
    items: [
      { label: 'Users',          path: '/admin/users' },
      { label: 'Roles',          path: '/admin/roles' },
      { label: 'Offices',        path: '/admin/offices' },
      { label: 'Hooks',          path: '/admin/hooks' },
      { label: 'Maker-Checker',  path: '/admin/maker-checker' },
      { label: 'Notifications',  path: '/admin/notifications' },
      { label: 'TPP Management', path: '/admin/tpp' },
    ],
  },
  {
    heading: 'Groups',
    items: [
      { label: 'Groups',   path: '/groups' },
      { label: 'Centers',  path: '/centers' },
    ],
  },
  {
    heading: 'System',
    items: [
      { label: 'Codes & Values',      path: '/system/codes' },
      { label: 'Global Config',       path: '/system/config' },
      { label: 'Floating Rates',      path: '/system/floating-rates' },
      { label: 'Taxes',               path: '/system/taxes' },
      { label: 'Account Algorithms',  path: '/system/account-algorithms' },
    ],
  },
  {
    heading: 'Open Banking',
    items: [
      { label: 'Consents', path: '/open-banking/consents' },
    ],
  },
]

export default function Sidebar() {
  return (
    <nav
      className="fixed left-0 top-0 bottom-0 flex flex-col overflow-y-auto"
      style={{ width: 'var(--sidebar-width)', background: 'var(--bg-sidebar)' }}
    >
      {/* Logo */}
      <div
        className="flex items-center gap-3 px-5 py-5 shrink-0"
        style={{ borderBottom: '1px solid rgba(255,255,255,0.07)' }}
      >
        <div
          className="flex items-center justify-center rounded-lg text-white font-display font-bold text-sm shrink-0"
          style={{
            width: 32,
            height: 32,
            background: 'var(--color-primary)',
            border: '1px solid rgba(255,255,255,0.12)',
          }}
        >
          C
        </div>
        <span className="font-display font-semibold text-white text-base tracking-tight">
          CBA
        </span>
      </div>

      {/* Nav sections */}
      <div className="flex-1 overflow-y-auto py-4 px-3">
        {NAV.map(section => (
          <div key={section.heading} className="mb-6">
            <p
              className="px-3 mb-1 text-[10px] font-semibold uppercase tracking-widest"
              style={{ color: 'rgba(255,255,255,0.3)' }}
            >
              {section.heading}
            </p>
            {section.items.map(item => (
              <NavLink
                key={item.path}
                to={item.path}
                end={item.exact}
                className={({ isActive }) =>
                  cn(
                    'flex items-center px-3 py-2 rounded-md text-sm font-medium transition-colors',
                    'duration-150',
                    isActive
                      ? 'text-[var(--color-accent)] bg-white/8'
                      : 'text-white/60 hover:text-white/90 hover:bg-white/5',
                  )
                }
              >
                {item.label}
              </NavLink>
            ))}
          </div>
        ))}
      </div>
    </nav>
  )
}
```

- [ ] **Step 2: Create `Topbar.tsx`**

```tsx
// web-react/src/app/layout/Topbar.tsx
import { useLocation } from 'react-router-dom'

// Map path prefixes to human-readable section names
const SECTION_LABELS: [string, string][] = [
  ['/dashboard',              'Dashboard'],
  ['/customers',              'Customers'],
  ['/accounts',               'Accounts'],
  ['/loans',                  'Loans'],
  ['/payments',               'Payments'],
  ['/tellers',                'Tellers'],
  ['/products/loan-products', 'Loan Products'],
  ['/products/deposit',       'Deposit Products'],
  ['/products/fixed',         'Fixed Deposits'],
  ['/products/recurring',     'Recurring Deposits'],
  ['/products/shares',        'Share Products'],
  ['/accounting',             'Accounting'],
  ['/cards/products',         'Card Products'],
  ['/cards/fraud',            'Fraud Rules'],
  ['/cards/settlement',       'Settlement'],
  ['/cards/disputes',         'Disputes'],
  ['/cards/terminal',         'Terminal Simulator'],
  ['/cards/api-keys',         'API Keys'],
  ['/cards/webhooks',         'Webhooks'],
  ['/cards/bins',             'BIN Management'],
  ['/cards/schemes',          'Scheme Config'],
  ['/cards/interchange',      'Interchange'],
  ['/cards',                  'Cards'],
  ['/reports/scheduler',      'CoB Scheduler'],
  ['/reports/mailing',        'Report Mailing'],
  ['/reports',                'Reports'],
  ['/admin/users',            'Users'],
  ['/admin/roles',            'Roles'],
  ['/admin/offices',          'Offices'],
  ['/admin/hooks',            'Hooks'],
  ['/admin/maker-checker',    'Maker-Checker'],
  ['/admin/notifications',    'Notifications'],
  ['/admin/tpp',              'TPP Management'],
  ['/groups',                 'Groups'],
  ['/centers',                'Centers'],
  ['/system/codes',           'Codes & Values'],
  ['/system/config',          'Global Config'],
  ['/system/floating-rates',  'Floating Rates'],
  ['/system/taxes',           'Taxes'],
  ['/system/account-algorithms', 'Account Algorithms'],
  ['/open-banking',           'Open Banking'],
]

function getSectionLabel(pathname: string): string {
  for (const [prefix, label] of SECTION_LABELS) {
    if (pathname.startsWith(prefix)) return label
  }
  return 'CBA Backoffice'
}

export default function Topbar() {
  const { pathname } = useLocation()
  const label = getSectionLabel(pathname)

  return (
    <header
      className="fixed top-0 right-0 flex items-center justify-between px-6 z-10"
      style={{
        left: 'var(--sidebar-width)',
        height: 'var(--topbar-height)',
        background: 'var(--bg-app)',
        borderBottom: '1px solid var(--color-border)',
      }}
    >
      <h1 className="font-display font-semibold text-lg" style={{ color: 'var(--color-text)' }}>
        {label}
      </h1>
      <div
        className="flex items-center justify-center rounded-full font-semibold text-sm shrink-0"
        style={{
          width: 36,
          height: 36,
          background: 'var(--color-primary)',
          color: 'white',
        }}
      >
        A
      </div>
    </header>
  )
}
```

- [ ] **Step 3: Create `Shell.tsx`**

```tsx
// web-react/src/app/layout/Shell.tsx
import { Outlet } from 'react-router-dom'
import Sidebar from './Sidebar'
import Topbar from './Topbar'

export default function Shell() {
  return (
    <div style={{ display: 'flex', minHeight: '100vh', background: 'var(--bg-app)' }}>
      <Sidebar />
      <div
        style={{
          marginLeft: 'var(--sidebar-width)',
          flex: 1,
          display: 'flex',
          flexDirection: 'column',
        }}
      >
        <Topbar />
        <main
          style={{
            marginTop: 'var(--topbar-height)',
            flex: 1,
            padding: '32px',
            overflowY: 'auto',
          }}
        >
          <Outlet />
        </main>
      </div>
    </div>
  )
}
```

- [ ] **Step 4: Run the dev server and verify the shell renders**

```bash
cd /Users/razormvp/CoreBanking/web-react
npm run dev
```

Open `http://localhost:5173`. Expected: dark shell renders with sidebar nav, topbar, and "Coming soon" placeholder content in the main area. Clicking nav links changes the pathname shown on the placeholder page.

- [ ] **Step 5: Run the build**

```bash
npm run build
```

Expected: Build succeeds.

- [ ] **Step 6: Commit**

```bash
git add web-react/src/app/layout/
git commit -m "feat(web-react): Shell + Sidebar + Topbar with all 9 nav sections"
```

---

## Task 8: Shared components

**Files:**
- Create: `web-react/src/shared/components/StatusBadge.tsx`
- Create: `web-react/src/shared/components/StatusBadge.test.tsx`
- Create: `web-react/src/shared/components/KpiCard.tsx`
- Create: `web-react/src/shared/components/PageHeader.tsx`
- Create: `web-react/src/shared/components/DataTable.tsx`
- Create: `web-react/src/shared/components/DataTable.test.tsx`
- Create: `web-react/src/shared/components/Modal.tsx`

- [ ] **Step 1: Write the failing `StatusBadge` test**

```tsx
// web-react/src/shared/components/StatusBadge.test.tsx
import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { StatusBadge } from './StatusBadge'

describe('StatusBadge', () => {
  it('renders the label', () => {
    render(<StatusBadge label="ACTIVE" variant="success" />)
    expect(screen.getByText('ACTIVE')).toBeInTheDocument()
  })

  it('applies success variant classes', () => {
    render(<StatusBadge label="ACTIVE" variant="success" />)
    const badge = screen.getByText('ACTIVE')
    expect(badge.className).toContain('success')
  })

  it('applies error variant classes', () => {
    render(<StatusBadge label="FAILED" variant="error" />)
    const badge = screen.getByText('FAILED')
    expect(badge.className).toContain('error')
  })
})
```

- [ ] **Step 2: Run the test — verify it fails**

```bash
npx vitest run src/shared/components/StatusBadge.test.tsx
```

Expected: FAIL — "Cannot find module './StatusBadge'"

- [ ] **Step 3: Implement `StatusBadge.tsx`**

```tsx
// web-react/src/shared/components/StatusBadge.tsx
import { cn } from './cn'

export type BadgeVariant = 'success' | 'warning' | 'error' | 'info' | 'neutral' | 'primary'

interface StatusBadgeProps {
  label: string
  variant: BadgeVariant
  className?: string
}

const variantStyles: Record<BadgeVariant, string> = {
  success: 'badge-success bg-[var(--color-success-bg)] text-[var(--color-success)]',
  warning: 'badge-warning bg-[var(--color-warning-bg)] text-[var(--color-warning)]',
  error:   'badge-error bg-[var(--color-error-bg)] text-[var(--color-error)]',
  info:    'badge-info bg-[var(--color-info-bg)] text-[var(--color-info)]',
  neutral: 'badge-neutral bg-[var(--bg-subtle)] text-[var(--color-muted)]',
  primary: 'badge-primary bg-[var(--color-primary)] text-white',
}

export function StatusBadge({ label, variant, className }: StatusBadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center px-2 py-0.5 rounded text-xs font-medium tabular-nums',
        variantStyles[variant],
        className,
      )}
    >
      {label}
    </span>
  )
}
```

- [ ] **Step 4: Run the test — verify it passes**

```bash
npx vitest run src/shared/components/StatusBadge.test.tsx
```

Expected: 3 tests pass.

- [ ] **Step 5: Write the failing `DataTable` test**

```tsx
// web-react/src/shared/components/DataTable.test.tsx
import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { DataTable, type ColumnDef } from './DataTable'

interface User { id: string; name: string }

const columns: ColumnDef<User>[] = [
  { key: 'name', header: 'Name', cell: row => row.name },
]

describe('DataTable', () => {
  it('renders column headers', () => {
    render(<DataTable columns={columns} data={[]} />)
    expect(screen.getByText('Name')).toBeInTheDocument()
  })

  it('renders row data', () => {
    const data: User[] = [{ id: '1', name: 'Alice' }]
    render(<DataTable columns={columns} data={data} />)
    expect(screen.getByText('Alice')).toBeInTheDocument()
  })

  it('shows empty state when data is empty', () => {
    render(<DataTable columns={columns} data={[]} emptyMessage="No users found" />)
    expect(screen.getByText('No users found')).toBeInTheDocument()
  })
})
```

- [ ] **Step 6: Run the test — verify it fails**

```bash
npx vitest run src/shared/components/DataTable.test.tsx
```

Expected: FAIL — "Cannot find module './DataTable'"

- [ ] **Step 7: Implement `DataTable.tsx`**

```tsx
// web-react/src/shared/components/DataTable.tsx
import { type ReactNode } from 'react'
import { cn } from './cn'

export interface ColumnDef<T> {
  key: string
  header: string
  cell: (row: T) => ReactNode
  className?: string
}

interface DataTableProps<T> {
  columns: ColumnDef<T>[]
  data: T[]
  emptyMessage?: string
  loading?: boolean
  className?: string
  getRowKey?: (row: T, index: number) => string
}

export function DataTable<T>({
  columns,
  data,
  emptyMessage = 'No data',
  loading = false,
  className,
  getRowKey = (_row, i) => String(i),
}: DataTableProps<T>) {
  return (
    <div className={cn('w-full overflow-x-auto', className)}>
      <table
        className="w-full text-sm"
        style={{ borderCollapse: 'collapse' }}
      >
        <thead>
          <tr style={{ borderBottom: '1px solid var(--color-border)' }}>
            {columns.map(col => (
              <th
                key={col.key}
                className={cn(
                  'px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider',
                  col.className,
                )}
                style={{ color: 'var(--color-muted)' }}
              >
                {col.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {loading ? (
            <tr>
              <td colSpan={columns.length} className="px-4 py-8 text-center text-sm" style={{ color: 'var(--color-muted)' }}>
                Loading…
              </td>
            </tr>
          ) : data.length === 0 ? (
            <tr>
              <td colSpan={columns.length} className="px-4 py-8 text-center text-sm" style={{ color: 'var(--color-muted)' }}>
                {emptyMessage}
              </td>
            </tr>
          ) : (
            data.map((row, i) => (
              <tr
                key={getRowKey(row, i)}
                className="transition-colors hover:bg-[var(--bg-subtle)]"
                style={{ borderBottom: '1px solid var(--color-border)', height: 44 }}
              >
                {columns.map(col => (
                  <td
                    key={col.key}
                    className={cn('px-4 py-2 tabular-nums', col.className)}
                    style={{ color: 'var(--color-text)' }}
                  >
                    {col.cell(row)}
                  </td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  )
}
```

- [ ] **Step 8: Implement `KpiCard.tsx`**

```tsx
// web-react/src/shared/components/KpiCard.tsx
import { cn } from './cn'

interface KpiCardProps {
  label: string
  value: string | number
  delta?: string
  deltaPositive?: boolean
  className?: string
}

export function KpiCard({ label, value, delta, deltaPositive, className }: KpiCardProps) {
  return (
    <div
      className={cn('rounded-xl p-6', className)}
      style={{
        background: 'var(--bg-card)',
        border: '1px solid var(--color-border)',
      }}
    >
      <p className="text-xs font-semibold uppercase tracking-wider mb-2" style={{ color: 'var(--color-muted)' }}>
        {label}
      </p>
      <p className="font-display text-2xl font-bold tabular-nums" style={{ color: 'var(--color-text)' }}>
        {value}
      </p>
      {delta !== undefined ? (
        <p
          className="mt-1 text-xs font-medium tabular-nums"
          style={{ color: deltaPositive ? 'var(--color-success)' : 'var(--color-error)' }}
        >
          {delta}
        </p>
      ) : null}
    </div>
  )
}
```

- [ ] **Step 9: Implement `PageHeader.tsx`**

```tsx
// web-react/src/shared/components/PageHeader.tsx
import { type ReactNode } from 'react'
import { cn } from './cn'

interface PageHeaderProps {
  title: string
  subtitle?: string
  actions?: ReactNode
  className?: string
}

export function PageHeader({ title, subtitle, actions, className }: PageHeaderProps) {
  return (
    <div className={cn('flex items-start justify-between gap-4 mb-8', className)}>
      <div>
        <h2 className="font-display text-xl font-semibold" style={{ color: 'var(--color-text)' }}>
          {title}
        </h2>
        {subtitle ? (
          <p className="mt-1 text-sm" style={{ color: 'var(--color-muted)' }}>
            {subtitle}
          </p>
        ) : null}
      </div>
      {actions ? <div className="flex items-center gap-3 shrink-0">{actions}</div> : null}
    </div>
  )
}
```

- [ ] **Step 10: Implement `Modal.tsx`**

```tsx
// web-react/src/shared/components/Modal.tsx
import { type ReactNode, useEffect, useRef } from 'react'
import { cn } from './cn'

interface ModalProps {
  open: boolean
  onClose: () => void
  title: string
  children: ReactNode
  footer?: ReactNode
  size?: 'sm' | 'md' | 'lg'
}

const sizeClasses = {
  sm: 'max-w-sm',
  md: 'max-w-md',
  lg: 'max-w-2xl',
}

export function Modal({ open, onClose, title, children, footer, size = 'md' }: ModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)

  // Always call hooks unconditionally — no early return before this point.
  useEffect(() => {
    const el = dialogRef.current
    if (!el) return
    if (open) {
      el.showModal()
    } else if (el.open) {
      el.close()
    }
  }, [open])

  useEffect(() => {
    const el = dialogRef.current
    if (!el) return
    const handler = () => onClose()
    el.addEventListener('close', handler)
    return () => el.removeEventListener('close', handler)
  }, [onClose])

  // Always render the <dialog> element — its visibility is controlled by
  // the native showModal()/close() API above, not by conditional rendering.
  // Removing this element from the DOM between open/close states would
  // reset scroll position, lose focus, and violate Rules of Hooks if
  // placed before the useEffect calls.
  return (
    <dialog
      ref={dialogRef}
      className={cn('rounded-xl w-full p-0 backdrop:bg-black/50', sizeClasses[size])}
      style={{
        background: 'var(--bg-card)',
        border: '1px solid var(--color-border)',
        boxShadow: '0 8px 32px rgba(0,0,0,0.16)',
      }}
      onClick={e => {
        // Close on backdrop click
        const rect = dialogRef.current?.getBoundingClientRect()
        if (rect && (e.clientX < rect.left || e.clientX > rect.right || e.clientY < rect.top || e.clientY > rect.bottom)) {
          onClose()
        }
      }}
    >
      <div className="flex items-center justify-between px-6 py-4" style={{ borderBottom: '1px solid var(--color-border)' }}>
        <h3 className="font-display font-semibold text-lg" style={{ color: 'var(--color-text)' }}>
          {title}
        </h3>
        <button
          onClick={onClose}
          className="flex items-center justify-center rounded-md w-8 h-8 text-lg transition-colors hover:bg-[var(--bg-subtle)]"
          style={{ color: 'var(--color-muted)' }}
          aria-label="Close"
        >
          ×
        </button>
      </div>
      <div className="px-6 py-5">{children}</div>
      {footer ? (
        <div className="px-6 py-4 flex items-center justify-end gap-3" style={{ borderTop: '1px solid var(--color-border)' }}>
          {footer}
        </div>
      ) : null}
    </dialog>
  )
}
```

- [ ] **Step 11: Run all shared component tests**

```bash
cd /Users/razormvp/CoreBanking/web-react
npx vitest run src/shared/components/
```

Expected: 6 tests pass (3 StatusBadge + 3 DataTable).

- [ ] **Step 12: Run the build**

```bash
npm run build
```

Expected: Build succeeds.

- [ ] **Step 13: Commit**

```bash
git add web-react/src/shared/
git commit -m "feat(web-react): StatusBadge, DataTable, KpiCard, PageHeader, Modal shared components"
```

---

## Task 9: `vercel.json` + CI job

**Files:**
- Create: `web-react/vercel.json`
- Modify: `.github/workflows/web-ci.yml`

- [ ] **Step 1: Create `web-react/vercel.json`**

```json
{
  "framework": "vite",
  "buildCommand": "npm run build",
  "outputDirectory": "dist",
  "rewrites": [
    { "source": "/((?!api/).*)", "destination": "/index.html" }
  ],
  "headers": [
    {
      "source": "/assets/(.*)",
      "headers": [
        { "key": "Cache-Control", "value": "public, max-age=31536000, immutable" }
      ]
    }
  ]
}
```

- [ ] **Step 2: Read the current `web-ci.yml`**

```bash
cat /Users/razormvp/CoreBanking/.github/workflows/web-ci.yml
```

- [ ] **Step 3: Add `react-deploy` job to `web-ci.yml` (append after the existing Angular jobs)**

Add the following job to the existing `web-ci.yml` — do NOT modify any existing Angular jobs:

```yaml
  react-deploy:
    name: React → Vercel Preview
    runs-on: ubuntu-latest
    if: >
      github.ref == 'refs/heads/main' ||
      github.event_name == 'pull_request'
    defaults:
      run:
        working-directory: web-react
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '22'
          cache: 'npm'
          cache-dependency-path: web-react/package-lock.json
      - run: npm ci
      - run: npm run build
      - run: npx vitest run
      - name: Deploy to Vercel (preview)
        run: |
          npm i -g vercel@latest
          vercel pull --yes --environment=preview --token=$VERCEL_TOKEN
          vercel build --token=$VERCEL_TOKEN
          vercel deploy --prebuilt --token=$VERCEL_TOKEN
        env:
          VERCEL_TOKEN: ${{ secrets.VERCEL_TOKEN_REACT }}
          VERCEL_ORG_ID: ${{ secrets.VERCEL_ORG_ID }}
          VERCEL_PROJECT_ID: ${{ secrets.VERCEL_PROJECT_ID_REACT }}
```

- [ ] **Step 4: Verify `web-ci.yml` is valid YAML**

```bash
cd /Users/razormvp/CoreBanking
node -e "const yaml = require('js-yaml'); yaml.load(require('fs').readFileSync('.github/workflows/web-ci.yml', 'utf8')); console.log('YAML valid')" 2>/dev/null || python3 -c "import yaml, sys; yaml.safe_load(open('.github/workflows/web-ci.yml')); print('YAML valid')"
```

Expected: "YAML valid"

- [ ] **Step 5: Final full test run**

```bash
cd /Users/razormvp/CoreBanking/web-react
npx vitest run
```

Expected: All tests pass.

- [ ] **Step 6: Final build**

```bash
npm run build
```

Expected: Build succeeds with no TypeScript errors.

- [ ] **Step 7: Commit everything**

```bash
cd /Users/razormvp/CoreBanking
git add web-react/vercel.json .github/workflows/web-ci.yml
git commit -m "feat(web-react): vercel.json SPA config + react-deploy CI job"
```

---

## Self-Review

### Spec coverage

| Design doc requirement | Covered by task |
|------------------------|----------------|
| Vite 6 + React 19 + TypeScript strict | Task 1 |
| Tailwind CSS v4 CSS-first config (`@theme`) | Task 2 |
| Epilogue (Google Fonts) + Geist (npm) | Task 2 |
| OKLCH design tokens as CSS custom properties | Task 2 |
| `tabular-nums` globally on financial data | Task 2 |
| `prefers-reduced-motion` respected | Task 2 |
| shadcn/ui `components.json` + `cn()` | Task 3 |
| AuthContext with bypass flag + roles | Task 4 |
| Axios apiClient with auth interceptor | Task 5 |
| `.env.local` + `.gitignore` | Task 5 |
| All 57 routes defined | Task 6 |
| PlaceholderPage | Task 6 |
| Dark shell: Shell + Sidebar + Topbar | Task 7 |
| All 9 nav sections + active state | Task 7 |
| StatusBadge (6 variants) | Task 8 |
| DataTable (typed, empty state, loading) | Task 8 |
| KpiCard | Task 8 |
| PageHeader | Task 8 |
| Modal (accessible dialog element) | Task 8 |
| `vercel.json` with SPA rewrite | Task 9 |
| `react-deploy` CI job (additive) | Task 9 |
| Tests for all shared components | Tasks 3, 4, 5, 8 |
| Vitest + React Testing Library | Task 1 |

### Placeholder scan

No TBD, TODO, or "similar to Task N" patterns found. All code steps contain complete, ready-to-use code.

### Type consistency

- `ColumnDef<T>` defined in Task 8, used in `DataTable<T>` in the same task. ✓
- `Role` type defined in `AuthContext.tsx` Task 4, not referenced in later tasks. ✓
- `cn()` imported as `@/shared/components/cn` consistently across Shell, Sidebar, all shared components. ✓
- `BadgeVariant` defined in `StatusBadge.tsx` and used in its own props. ✓
