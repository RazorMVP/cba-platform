> **Status:** merged — PR #183, merge commit `bdc5a5d`. Follow-ups: #184 (Dashboard crash found in the browser check, `f7cd14c`), #185 (portal tests in CI, `d5da244`). Approved 2026-10-19 (user chose option 1). Plan given in chat, not plan mode; body verbatim.

# Plan: backlog 18, partner-portal React Router 6 → 7

**What we found**
- Both open alerts (#163, #164) are fixed in `react-router` **7.18.0**; latest 7.x is 7.18.4.
  - #164: open redirect through a backslash in `<Link>` or `useNavigate`. This one applies to us.
  - #163: an SSR hydration issue. The portal is a client-side app with no server rendering, but the dependency is still in range.
- Usage is small: 20 files, all `react-router-dom` imports. The app uses `createBrowserRouter` + `RouterProvider`, `Link`, `NavLink`, `Navigate`, `Outlet`, `useNavigate`, and `router.navigate` in `apiClient.ts`; tests use `MemoryRouter`, `Routes`, `Route`. Every one of these exists in v7.
- **v7 behaviour changes:** only `startTransition` affects this app. Navigating to a lazy page now keeps the current page visible until the new one loads, instead of flashing the spinner. That's the intended UX, but some tests may need `findBy`.
- All links are absolute, so the splat-path change does nothing here. There are no loaders, actions, fetchers or SSR.
- Node: v7 needs ≥20. CI uses 22, and local is 22.23. The Dockerfile is nginx only.
- 8.x is out of scope: it is a major release (Dependabot ignores majors) and needs React ≥19.2.7.

**Choice: how far to move?**
1. **Switch to the `react-router` package (recommended).** Replace `react-router-dom` with `react-router` ^7.18.4. Import `RouterProvider` from `react-router/dom` and everything else from `react-router`. This touches about 20 import lines and is what the v7 upgrade guide recommends; v8 deletes `react-router-dom`, so a later v8 move becomes a version bump.
2. **Bump only:** `react-router-dom` ^6.30.6 → ^7.18.4, no code changes. Smallest diff, but the import change is still owed before v8.

**Steps (option 1)**
1. Branch `fix/partner-portal-react-router-7`. Copy this plan to `docs/plans/2026-10-19-backlog-18-react-router-7.md`.
2. Change the dependencies, updating only the lockfile because of the npm crash we hit earlier with Vitest; then prove the install with a plain `npm ci` and `npm ls`.
3. Rewrite the imports: `main.tsx` imports `RouterProvider` from `react-router/dom`, and the other 19 files import from `react-router`.
4. Verify:
   - `npm test`: 78 tests; fix any that now need `findBy`;
   - `npm run build`, `npm run lint`;
   - `npm audit` → 0;
   - a browser check of the built app: login redirect, sidebar navigation, unknown path → `/dashboard`.
5. Docs:
   - CLAUDE.md: partner-portal React Router row;
   - backlog 18 marked done;
   - cba-log cont. 39;
   - gate grep.
6. Open the PR. After it merges, the two Dependabot alerts should close without any dismissals.

**Time:** about 30–45 minutes of work, plus about 10 minutes of CI.
