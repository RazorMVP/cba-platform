> **Status:** merged — PR #148, merge commit `2540fea`. Approved 2026-10-13.  
> Recovered verbatim from the session transcript (the approved `ExitPlanMode` plan); the body below is unedited.

# card-service — Keycloak realm roles map to ROLE_* (+ close the CUSTOMER gap it would open)

## Context
#147 fixed the backend: Keycloak puts realm roles in the nested `realm_access.roles` claim, and Spring's default JWT converter only maps `scope` → `SCOPE_*`. card-service has the same gap, but worse: it configures **no** converter at all (`SecurityConfig.java:119` `cardApiChain`, `:144` `publicChain` — `oauth2.jwt(jwt -> {})`). So every one of its 58 `@PreAuthorize` checks plus the 2 URL rules (`/api/v1/simulate/**`) refuses every real Keycloak token. Only `DevAuthBypassFilter` (dev) and API keys (`ROLE_API_KEY`) work today.

**Catch found during exploration:** 8 endpoints admit `CUSTOMER` with **no ownership check**:
- `CardApiController` 123/137/156/173/189/198: get card, controls, limits, PIN change, authorizations, settled transactions
- `DisputeController` 80/138: raise, withdraw

Today they're safe only by accident (no roles → refused). Mapping roles without fixing them lets any customer read or freeze any card by id, the same IDOR #147 closed in the backend. card-service has no Keycloak-user → customer link (`cards.customer_id` exists; `customers.keycloak_id` lives in the backend DB).

**Decision (user chose option 1, 2026-10-13):** remove `CUSTOMER` from those 8 endpoints (staff + API key only). Nothing calls them as a customer today: the Angular Cards screens are staff-only, partners use API keys, and the Flutter app isn't built. Customer card self-service gets proper ownership later (a backend `/self/cards` proxy or a sub→customer lookup).

Standing rules: branch + PR (`fix/card-service-realm-roles` off `main` `73d209f`); merge only on the user's word with CI green; OWASP gate stays blocking.

## 1. card-service code
`card-service/src/main/java/com/cba/card/config/SecurityConfig.java`
- Add `@Bean JwtAuthenticationConverter jwtAuthenticationConverter()` with `setJwtGrantedAuthoritiesConverter(SecurityConfig::realmRoles)`.
- Add `static Collection<GrantedAuthority> realmRoles(Jwt)`. Copy it verbatim from `backend/.../config/SecurityConfig.java:140-161`: reads `realm_access.roles`, emits `ROLE_<role>`, and returns empty if the claim is missing or malformed.
- Wire it into both chains: `.oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(jwtAuthenticationConverter())))` (lines 119 and 144).
- Keep the default principal claim (`sub`). `CardApiController.resolveUserId` (331-339) relies on `auth.getName()` being the Keycloak UUID.
- Dropping JWT `SCOPE_*` breaks nothing: no `hasAuthority`/`SCOPE_` check exists. API-key `SCOPE_*` (`ApiKeyAuthentication`) is untouched.

`openbanking/CardApiController.java` (6 annotations): `hasRole('API_KEY') or hasAnyRole('ADMIN', 'TELLER', 'CUSTOMER')` → `hasRole('API_KEY') or hasAnyRole('ADMIN', 'TELLER')`.
`dispute/DisputeController.java` 80, 138: `hasAnyRole('ADMIN','TELLER','CUSTOMER')` → `hasAnyRole('ADMIN','TELLER')`.

Not touched: the `/3ds/acs/**` and `/api/v1/internal/**` chains (no JWT, permitAll by design), fep-service (no Spring Security), and the Keycloak realm (roles already reach `realm_access` via the built-in `roles` scope).

## 2. Tests
- **New `card-service/src/test/java/com/cba/card/config/SecurityConfigTest.java`** (unit, 3 tests mirroring `backend/src/test/java/com/cba/config/SecurityConfigTest.java`): nested claim → `ROLE_*`; missing claim or roles → empty; the converter bean maps roles and keeps `sub` as the name. `new SecurityConfig()` works because its dependencies are `@Autowired` fields.
- **New `RealmRoleMappingIntegrationTest`** (end to end through the real chains and converter, `-Pfull-integration`):
  - Extend the base `AbstractCardIntegrationTest` decoder. A token `test-roles:ADMIN,TELLER` decodes to a `Jwt` with `sub` plus `realm_access.roles`; every other token still throws `BadJwtException`, so existing tests are unchanged.
  - Cases:
    - publicChain `GET /api/v1/bins`: ADMIN → 200, TELLER → 403, no roles → 403.
    - cardApiChain `GET /card-api/v1/api-keys`: ADMIN → 200, TELLER → 403.
    - `GET /card-api/v1/cards/{random}`: CUSTOMER → 403, TELLER → 404.
    - `/api/v1/simulate/network/echo`: CUSTOMER → 403 (URL rule).
  - Check at implementation: `RateLimitService` catches Redis errors (line 85). Confirm it fails open in the test profile; if not, point the `/card-api` cases at a mocked `StringRedisTemplate`.

## 3. Docs and housekeeping
- `docs/card-api-reference.html`:
  - Roles section (~192-199): roles come from Keycloak `realm_access.roles` → `ROLE_<role>`; `API_KEY` is the `Authorization: ApiKey` authority, not a realm role.
  - `CUSTOMER` row: no card self-service yet.
  - Endpoint rows 244 and 267-269 → staff/API key.
  - Copy to `docs-site/static/`.
- Postman: update any card-service request descriptions that name CUSTOMER for these endpoints (script with `ensure_ascii=True`, same as before).
- Regenerate `card-service/docs/openapi-snapshot.yaml` (expect no diff; `@PreAuthorize` isn't in the spec).
- `CLAUDE.md`: card-service table row, a card-service RBAC note, and the remaining follow-ups.
- `cba-log.md` cont. 29 with the gate grep proof, including untracked files.
- Memory `project_gl_posting_decisions.md`: mark the card-service follow-up done.
- **Follow-ups noted, not fixed here:**
  - The backend `CardServiceClient` calls card-service `/api/v1/cards/**` with **no Authorization header**, so the Open Banking card merge gets 401 today. It needs a service-account token (client credentials).
  - Customer card self-service with ownership.

## 4. Verification
- Before edits: `cd card-service && ./mvnw -o clean test` for a baseline (expect 118).
- After: `./mvnw -o clean test` (118 + 3) and `DOCKER_HOST=unix://$HOME/.docker/run/docker.sock ./mvnw clean verify -Pfull-integration` (124 + new IT, all green; snapshot unchanged). Backend untouched; no backend or web run needed.
- Gate grep (tracked + untracked Java) → `@PreAuthorize`-only changes → docs updated per §3.
- PR → CI (`Card Service CI gate` + security checks) green → merge on the user's word.
