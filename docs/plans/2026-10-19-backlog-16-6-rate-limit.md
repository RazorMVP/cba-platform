> **Status:** merged — PR #188, merge commit `afabb47` (replaced #187, whose first commit held a JWT-shaped test literal that Gitleaks flags). Two bugs outside the plan were found by its tests and fixed (partner API keys never authenticated outside dev bypass; the 429 body threw). The commit security review then found 2 more — the partner-login tier came from a 24 h-old token claim, and Keycloak callers shared one bucket per client application; user chose "fix both" (tier from the database; bucket `jwt:<azp>:<sub>`). See cba-log cont. 43. Approved 2026-10-19: user chose **"1a"** (organisation tier; sandbox orgs BASIC, anonymous SANDBOX). Plan given in chat, not plan mode; body verbatim. The card-service limiter's same flaws went to backlog item 20 (#186).

# Plan: backlog 16 + 6, backend rate limiter

**What we found**
- `RateLimitFilter` runs **before** authentication (`SecurityConfig.java:51`), so everything it uses is unverified:
  1. **Tier (item 16):** `X-Rate-Tier: ENTERPRISE` from any caller raises their limit from 100 to 2,000 req/min.
  2. **Identity:** `azp`/`sub` come from an *unverified* token. A new fake `azp` on each request bypasses the limit entirely, and copying a victim's `azp` drains *their* bucket.
  3. **IP:** `X-Forwarded-For` is trusted from anyone (`forward-headers-strategy` isn't configured). Anonymous endpoints such as partner **login** can be brute-forced by rotating that header.
  4. **Item 6:** a partner's real tier is never used. Everyone gets BASIC.
- Tier data: the organisation and the API key each have a `tier` column. Both are always `BASIC`; nothing in code changes them. Per the partner docs, support upgrades a tier by hand.

**The fix: rate-limit after authentication, using verified data only**
1. **Move the filter** to just before Spring Security's authorization step, after every authentication filter. Identity and tier then come only from the verified login:

   | Caller | Bucket | Tier |
   |---|---|---|
   | Partner API key / partner JWT | `partner:<orgId>`, shared by both | organisation's tier (decision 1) |
   | Keycloak token (staff, customer, TPP) | `jwt:<azp or sub>`, verified | BASIC (unchanged) |
   | Anonymous | `ip:<client IP>` | decision 2 |
2. **Delete the `X-Rate-Tier` header** handling.
3. **Client IP from a trusted proxy only:** set `server.forward-headers-strategy: native`. Tomcat then accepts `X-Forwarded-For` only from private-network proxies such as the ingress.
4. **Don't count CORS preflights (`OPTIONS`).** Browsers send one before many API calls, and they carry no credentials.
5. **`RateLimitEventNotifier`:** take the org from the verified login, not from re-parsing the token. This also removes its extra API-key database lookup.
6. **Trade-off:** a request with an invalid token now gets 401 before it's counted. Rejecting it costs us only a local signature check, and today's limit can be skipped anyway.

**Tests**
- Unit (`RateLimitFilterTest`, new): the header is ignored; each caller type maps to the right bucket and tier; OPTIONS is skipped; a sandbox org gets the right tier.
- Integration with the real filter chain:
  - a forged token plus `X-Rate-Tier: ENTERPRISE` → 401 with no limit raised;
  - a PRO partner API key → `X-RateLimit-Limit: 500`;
  - anonymous → the IP limit.
- Full `-Pfull-integration` run (currently 902 tests).

**Docs:** partner-docs `rate-limiting.md` and `core-concepts.md` fixed to agree, CLAUDE.md, backlog 6 and 16 ✅, cba-log, api-reference / Postman if the gate grep requires them, and the plan copied to `docs/plans/`.

**Time:** about 2–3 hours, plus CI.

**Decision 1: which tier counts for a partner?**
1. **The organisation's tier (recommended).** It's the partner's plan, which is what the docs say support upgrades, and the API key and JWT paths share one bucket so the limit can't be doubled.
2. The API key's own tier. Each key could have a different limit; the JWT path would still use the organisation's tier.

**Decision 2: sandbox partners and anonymous callers.** The docs disagree: `rate-limiting.md` says sandbox keys and unauthenticated callers get **SANDBOX (30/min)**, while `core-concepts.md` says "sandbox accounts always use **BASIC**".
- a. **Sandbox orgs BASIC, anonymous SANDBOX 30/min per IP (recommended).** Tighter on unauthenticated traffic like login, unchanged for sandbox developers. Fix `rate-limiting.md` to match.
- b. Both SANDBOX 30/min. Matches `rate-limiting.md`, but cuts sandbox developers from 100 to 30.
- c. Both BASIC 100/min. No behaviour change; fix both docs to match.
