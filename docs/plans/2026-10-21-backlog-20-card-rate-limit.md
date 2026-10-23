> **Status:** merged — PR #189, merge commit `c69b860`. Approved 2026-10-21 in plan mode (user: "Delete the branch, then start 20"). Body verbatim.

# Backlog 20 — card-service rate limiter on the verified caller

## Context
- `/card-api/v1/**` is rate-limited by `card-service/.../config/RateLimitFilter`. `SecurityConfig` adds it **before `UsernamePasswordAuthenticationFilter`**, which is ahead of every auth filter (chain 2 `cardApiChain`, and chain 3 `publicChain`, where it does nothing because only `/card-api/v1/` is limited). It buckets on input nobody has checked:
  - **`ApiKey`:** SHA-256 of whatever key was sent, valid or not → a random key per request gets a fresh bucket (at BASIC 100).
  - **`Bearer`:** `sub` from a hand-parsed, **unverified** JWT payload → forge a new `sub` per request for a fresh bucket, or forge a victim's `sub` to drain theirs.
  - **IP:** first `X-Forwarded-For` value from anyone.
- **Same bug as the backend had:** the 429 body is `Map.of("data", (Object) null, …)`. `Map.of` throws on null, so an over-limit request gets a 500, never its 429.
- The tier is already correct (`api_keys.tier`). Only the identity and the 429 are wrong.
- This is the same fix #188 made in the backend (`backend/.../config/RateLimitFilter.resolveCaller`). Mirror it.

## Step 0 — Delete the closed #187's branch (user asked)
`git push origin --delete fix/backend-rate-limit-verified-identity`, plus the local copy if any. Verified: #187 is CLOSED, unmerged, and #188 replaced it.

## Step 1 — Branch `fix/card-service-rate-limit-verified-identity` off `main`

## Changes (card-service only)

**`config/RateLimitFilter.java`** (rewrite to the backend's shape):
- Skip `OPTIONS`; keep `RATE_LIMITED_PREFIXES = /card-api/v1/` and the exclusions unchanged (scope doesn't widen).
- `record Caller(String identity, Tier tier)`; package-private `resolveCaller(Authentication, String remoteAddr)`:
  - `ApiKeyAuthentication` → `key:<apiKeyId>` at the key's `tier` (read from the DB on this request by `ApiKeyService.verify`)
  - `JwtAuthenticationToken` → `jwt:<azp>:<sub>` (or whichever one exists), BASIC: the backend rule
  - null / unauthenticated / `AnonymousAuthenticationToken` → `ip:<request.getRemoteAddr()>`, SANDBOX
  - anything else (dev-bypass token) → `user:<name>`, BASIC
- Delete `extractJwtSub`, `resolveIp`, and the request-side SHA-256. The filter never reads `Authorization` or `X-Forwarded-For` again.
- 429 body → `objectMapper.writeValueAsString(ApiResponse.error("RATE_LIMIT_EXCEEDED", …))`. Uses card-service's existing `common/ApiResponse.error`.

**`openbanking/apikey/ApiKeyAuthentication.java`:** add `tier` (from `key.getTier()`), plus a `getTier()` getter. This avoids a second DB lookup.

**`config/RateLimitService.java`:** replace `checkByKeyHash` / `checkBySubject` / `checkByIp` with one `check(String identity, Tier tier)` → Redis key `rl:card:<identity>`. Remove the now-unused `ApiKeyRepository` dependency. No other callers (verified by grep).

**`config/SecurityConfig.java`:** both chains: `addFilterBefore(rateLimitFilter, AuthorizationFilter.class)`. Fix the field comment ("before auth" → after authentication, before authorization).

**`application.yml`:** `server.forward-headers-strategy: native` (same comment as the backend). The k8s ingress controller connects from a private-network address, which Tomcat's `RemoteIpValve` trusts by default, so real clients keep their own IP.

**No servlet-registration change needed:** `RateLimitFilter` / `ApiKeyAuthFilter` are `@Component`s, so Boot also registers them as servlet filters. They run **after** Spring Security (lowest precedence), and `OncePerRequestFilter` skips them there, as in the backend. The IT below proves this.

## What callers will see
- Valid API key: own bucket at its tier (unchanged limits).
- Invalid / random API key: counted on the caller's IP at SANDBOX (30), then 401. It used to get a fresh BASIC bucket each time.
- Invalid bearer token: 401 from the JWT filter **before** counting (no `X-RateLimit-*` headers).
- Valid Keycloak token: per user within the client, BASIC (unchanged limit; the bucket was `sub` only).
- Over limit: a real 429 with the standard envelope (was a 500).
- Old Redis keys (`rl:card:<hash16>`, `rl:card:jwt:<sub>`) expire within 60 s; no migration.

## Tests
- **New `config/RateLimitFilterTest` (unit):** each `resolveCaller` branch, including azp-only and sub-only; `X-Forwarded-For` ignored; `OPTIONS` skipped; non-card-api path passes through; 429 writes valid JSON with `RATE_LIMIT_EXCEEDED` and `Retry-After: 60`.
- **New `config/RateLimitIntegrationTest`** (real HTTP, `AbstractCardIntegrationTest`; Redis is down in tests, so it fails open and `X-RateLimit-Limit` shows the tier):
  1. Valid PRO API key (row inserted with `JdbcTemplate`, SHA-256 hash) on `GET /card-api/v1/cards` → 200, limit `500`.
  2. Random API key → 401, limit `30` (anonymous IP bucket; was a fresh BASIC bucket).
  3. Forged bearer → 401, **no** `X-RateLimit-Limit` header. Build the token at runtime (Gitleaks `jwt` rule, as in the backend's `RateLimitIT`).
  4. Valid `ROLES_TOKEN` bearer (ADMIN) → 200, limit `100`.
- Run: `cd card-service && ./mvnw -o clean test`, then `DOCKER_HOST=unix://$HOME/.docker/run/docker.sock ./mvnw -o clean verify -Pfull-integration`. Today's counts are 125 unit and 161 full; expect more than both, all green.

## Docs (Session Completion Gate)
- `docs-site/docs/rate-limiting.md` rows 166–167: Card API → "the verified API key (per key, at its tier)" / "verified token's client + user".
- `CLAUDE.md`:
  - Rate Limiting section: the card-service file-tree line, the Redis key list (`rl:card:key:<id>`, `rl:card:jwt:<azp>:<sub>`, `rl:card:ip:<addr>`, `rl:card:user:<name>`), and drop "card-service's limiter still keys on unverified input (backlog 20)";
  - card-service versions row: new test counts.
- `docs/deferred-backlog.md`: item 20 → "✅ DONE (cont. 44)".
- `cba-log.md` cont. 44, including the gate-grep proof line. No `@*Mapping` changes are expected, so no api-reference or Postman edits; the grep confirms.
- Copy this plan to `docs/plans/2026-10-21-backlog-20-card-rate-limit.md` and add it to `docs/plans/README.md`.

## Ship
- Commit, push, and open a PR.
- Wait for CI. The required gates are Card Service CI gate, Gitleaks, Dependency Review and both CodeQL checks.
- Merge only on your word.
