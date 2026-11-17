# Backlog 19 — partner portal reaches its API through a same-origin `/api` path

> Status: approved 2026-11-17 (user: "Can #19 be done now?"; plan approved; D1 not answered → recommended D1a taken). In progress: PR open. The Vercel rewrite waits on backlog 8.

## Context

| Problem today | After this PR |
|---|---|
| `apiClient.ts` falls back to `https://sandbox.nubbank.com/api/v1`, a host with no DNS record. `VITE_API_URL` is set nowhere, so the live Vercel portal and the Docker image both call a dead host | The portal calls the relative `/api/v1` on its own origin. Whoever serves it forwards `/api/` to the backend |
| Docker image: `nginx.conf` has no `/api` proxy, so `docker compose --profile app` can't log in to the local backend | nginx forwards `/api/` to `${API_UPSTREAM}` (default `http://backend:8080`, the compose service). Login works locally |
| `npm run dev` has no proxy | The Vite dev server proxies `/api` to `localhost:8080` |
| Vercel: nothing to forward to yet | **Unchanged and still broken until item 8 gives a public host.** Calls now fail as a 404 on the portal's own domain, not a DNS error. Going live later = one `vercel.json` rewrite (step in deferred-backlog) |

Same origin also means no CORS, and the CSP `connect-src` can drop `localhost:8080` and `*.nubbank.com`; `'self'` is enough.

**Not in this PR (blocked on item 8):** choosing the public API host, the Vercel rewrite to it, a live login check.

## Decision for you
**D1. `VITE_API_URL`.**
- **a (recommended):** keep it as an optional override (`VITE_API_URL ?? '/api/v1'`). The build never fails and there is no dead-host fallback.
- b: remove it; the path is always `/api/v1`.

## Files
- `partner-portal/src/app/api/apiClient.ts`: fallback `/api/v1`.
- `partner-portal/nginx.conf` → **`nginx.conf.template`**: add `location /api/ { proxy_pass ${API_UPSTREAM}; … }` (Host, X-Forwarded-For/Proto; `Authorization` passes by default); CSP `connect-src 'self'`. The official nginx image runs envsubst on `/etc/nginx/templates/*.template` at start; limit it to `API_UPSTREAM` (`NGINX_ENVSUBST_FILTER`) so nginx's own `$uri`/`$host` survive.
- `partner-portal/Dockerfile`: copy the template to `/etc/nginx/templates/default.conf.template`; `ENV API_UPSTREAM=http://backend:8080`.
- `infrastructure/docker-compose.yml` `partner-portal`: `API_UPSTREAM: http://backend:8080`.
- `partner-portal/vite.config.ts`: `server.proxy['/api'] → http://localhost:8080`.
- `partner-portal/vercel.json`: unchanged now; the pending rewrite `{ "source": "/api/(.*)", "destination": "https://<host>/api/$1" }` is written down in the backlog item.
- Docs: `docs/deferred-backlog.md` item 19 (done vs left), `docs/integration-runbook.md`, CLAUDE.md partner-portal row, cba-log cont. 71, `docs-site/docs/sdks-tools.md` note that `sandbox.nubbank.com` isn't live yet.

## Tests
- `apiClient` test: base URL is `/api/v1` when `VITE_API_URL` is unset (`vi.stubEnv`).
- Existing portal suite (`npm test`) and `npm run build` green.

## Verification
1. `cd partner-portal && npm test && npm run build`; `grep -c sandbox.nubbank dist/assets/*.js` → 0.
2. `docker build -t cba-partner-portal:local partner-portal`; `docker run --rm cba-partner-portal:local nginx -T` shows `proxy_pass http://backend:8080;` and the SPA `try_files $uri` intact.
3. Local stack: `curl -i localhost:3000/api/v1/partners/auth/login -d …` reaches the backend (a Spring 4xx, not an nginx 404/502); log in through the portal in a browser.
4. Docs gate grep pasted; branch + PR; merge on "merge N" with required gates green.
