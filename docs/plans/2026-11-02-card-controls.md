# Backlog 5: enforce card controls (contactless, online/CNP, international)

> Status: merged 2026-11-03 — #222 (`f5dfdfa`). Approved with no D1/D2 reply → defaults **D1a + D2a**.

## Context
`PUT /card-api/v1/cards/{id}/controls` accepts `freeze`, `contactlessEnabled`, `cnpEnabled` and `internationalEnabled`. Only `freeze` does anything: it blocks or unblocks the card. The other three are dropped (`CardApiController.java:148`: "For now these are advisory flags returned as-is"). They are never stored and never checked. A customer who turns contactless off can still pay by tap. Goal: store the three flags per card and decline transactions that a switched-off channel would allow.

## What was checked (read-only)
| # | Finding | Where |
|---|---|---|
| 1 | The flags aren't stored anywhere: `cards` has no columns for them; `card_limits` holds amounts only | `Card.java`, V1 |
| 2 | `authorize()` does: card lookup (RC 14) → fraud (RC 62/05) → funds reservation (hold / wallet) → approve. A control check must come **before** funds are reserved, or a declined tap still places a hold | `CardAuthorizationService.java:75-200` |
| 3 | DE22 (POS entry mode) reaches card-service as `posEntryMode`. One correct classifier already exists, `InterchangeQualificationEngine.mapChannel`: 02/05/07/80/90/91 = card present, everything else (01 manual, 81 e-commerce, blank) = CNP. `FraudContext.isCardNotPresent()` only knows `CNP`/`010`, so I'm not reusing it | `InterchangeQualificationEngine.java:177`, `FraudContext.java:47` |
| 4 | Contactless = DE22 `07x` (contactless chip) or `91x` (contactless magstripe); the simulator sends `071` | `TerminalSimulatorService.mapEntryMode` |
| 5 | **No transaction country reaches card-service.** fep-service builds `AuthorizationRequest` without DE19 (Acquiring Institution Country, ISO 3166 numeric), although its packagers define DE19 | `AuthorizationHandler.java:73-86`, `iso8583-*.xml` |
| 6 | The card's home country is `bin_ranges.country_code`, ISO **alpha-3** (`USA`, `KEN`, `GHA`). Some demo ranges have none. The JDK has no numeric country codes, so comparing against DE19 needs a numeric → alpha-3 table | `V2__card_demo_data.sql:17-27`, `BinRangeRepository.findByPan8` |
| 7 | Advice (0120, `recordAdvice`) and reversals (0400, `reverse`) don't go through `authorize()`. Controls must never block a reversal, and they won't | `CardAuthorizationService.java:272, 299` |
| 8 | No webhook event exists for a control change. Freeze already fires `CARD.BLOCKED` / `CARD.UNBLOCKED` through `executeCommand` | web `webhooks.ts:6`, `CardService.executeCommand` |
| 9 | The web app never calls the controls endpoint. `Card` type in `cards.service.ts` has no control fields | `web/.../cards/` |

## Decisions for you
**D1 — what counts as "international"**
- **D1a (recommended):** the acquiring country (DE19) differs from the card's home country (its BIN range). fep-service starts passing DE19. A full ISO 3166-1 numeric → alpha-3 table goes in as a resource file. I'll generate it from the Debian `iso-codes` data via `pycountry`, installed in a scratch venv, and record the source in the file. A test checks every alpha-3 code against the JDK's ISO list.
- **D1b:** the transaction currency (DE49) differs from the card's account or wallet currency. No fep-service change, but a USD purchase inside Nigeria would count as international, and a naira purchase abroad wouldn't.
- **D1c:** contactless and CNP only. `internationalEnabled: false` is refused with `400 CONTROL_NOT_SUPPORTED` and stays a backlog item.

**D2 — international is off, but the country can't be determined** (only applies with D1a)
- **D2a (recommended): fail closed.**
  - If DE19 is missing, decline with RC 57.
  - If the card's BIN range has no country, `PUT …/controls` refuses `internationalEnabled: false` with `400 CARD_COUNTRY_UNKNOWN`. That way a customer is never told the switch is off when it can't work.
  - Effect: only customers who turned international off are affected. Their transactions from an acquirer that sends no DE19 are declined.
- **D2b: fail open.** If either country is unknown, the transaction counts as domestic and is approved.

Default if approved without a reply: **D1a + D2a**.

## Steps (branch `feat/card-controls`, one PR)
1. **Store the flags.** V15 adds `contactless_enabled`, `cnp_enabled`, `international_enabled` to `cards`: `BOOLEAN NOT NULL DEFAULT true`, so existing cards keep working. `Card` gets the three fields, returned on every card read.
2. **Update path.** Move the controller's logic into a new `CardService.updateControls(id, CardControlsRequest)` (`@Transactional`):
   - freeze still goes through `executeCommand` (block/unblock, same events);
   - each non-null flag is set; null leaves it unchanged;
   - D2a check for `internationalEnabled: false`;
   - when a flag changes, fire new event **`CARD.CONTROLS_CHANGED`** `{cardId, contactlessEnabled, cnpEnabled, internationalEnabled}`.
   
   The controller becomes a thin call to the service.
3. **One entry-mode classifier.** New `card/auth/PosEntryModes` with `isCardPresent(mode)` (the current `mapChannel` rule) and `isContactless(mode)` (`07`, `91`, `CONTACTLESS`). `InterchangeQualificationEngine.mapChannel` calls it, with no behaviour change. Fraud rules are left as they are.
4. **Country.**
   - fep-service `AuthorizationRequest` gains `acquiringCountry`, set from DE19 in `AuthorizationHandler` and in `FinancialHandler` if it builds its own request.
   - card-service `CardAuthRequest` gains `acquiringCountry`.
   - New `card/auth/CountryCodes.alpha3(numeric)` reads the resource table.
   - `BinService.issuingCountry(panPrefix)` uses the existing `findByPan8` and is queried only when international is off.
5. **Enforce.** `authorize()` checks controls right after the fraud decline check and before any funds call, for every request including balance inquiries:
   - contactless off and `isContactless` → RC **57**;
   - CNP off and not `isCardPresent` → RC **57**;
   - international off and the countries differ, or DE19 is missing (D2a) → RC **57**.

   RC 57 is "transaction not permitted to cardholder". Each decline is logged through the existing `logAndReturn`, so it appears in the authorization history, and an INFO line names the control.
6. **Terminal simulator.** `SimulateRequest` gains optional `acquiringCountry` (3 digits). When set, the 0100/0200 build adds DE19, so the international path can be tested end to end.
7. **Web.**
   - `Card` type gains the three flags.
   - `CardsService.updateControls(id, body)` calls `PUT /card-api/v1/cards/{id}/controls`.
   - Card detail Overview gets a "Controls" block with three toggles and an error line that shows the 400 message.
   - `webhooks.ts` event list gains `CARD.CONTROLS_CHANGED`.

## Tests
- `CardAuthorizationServiceTest`, with every decline asserting the backend is never called (no hold, no wallet change):
  - contactless off + `071` → 57;
  - CNP off + `010` / `812` / blank → 57;
  - CNP off + chip `051` → approved;
  - international off: DE19 `840` on a `USA` BIN → approved; `566` → 57; missing → 57;
  - all flags on → unchanged.
- `PosEntryModesTest`; `CountryCodesTest` (known pairs `566→NGA`, `840→USA`, `404→KEN`; every alpha-3 is a JDK ISO country; unknown → null).
- `CardServiceTest` `updateControls`:
  - flags saved;
  - null leaves a flag as it is;
  - event fired only on change;
  - freeze still blocks;
  - `CARD_COUNTRY_UNKNOWN`.
- `FepAuthorizeContractTest`: `acquiringCountry` binds. fep `AuthorizationHandlerTest`: DE19 copied.
- Integration (`-Pfull-integration`): V15 boots under `ddl-auto=validate`; snapshot regenerated (`Card` gains 3 fields).
- Web: card-detail spec covers the toggle call and the error message.

## Verification
- `cd card-service && ./mvnw -o test`, then `DOCKER_HOST=unix://$HOME/.docker/run/docker.sock ./mvnw clean verify -Pfull-integration`: green, new count reported (from 217 / 249).
- `cd fep-service && ./mvnw -o test`.
- `cd web && CI=true npx ng test --no-watch` + `npm run check:change-detection` + production build.
- PR CI: Card Service CI gate, Web CI gate, CodeQL, Gitleaks, Dependency Review. fep-service CI runs but isn't required.

## Docs (same PR)
- `docs/card-api-reference.html` + static copy: controls endpoint (enforced, RC 57, 400 errors), card fields, `CARD.CONTROLS_CHANGED` in the event catalogue.
- `docs/api-reference.html` + static copy: same rows in the Card Open Banking section.
- Postman + static copy: controls example bodies + error examples.
- `partner-docs/docs/webhooks.md`: new event.
- `CLAUDE.md`:
  - card-service row (cont. 54);
  - webhook catalogue row;
  - card controls note (DE22 classifier, DE19, fail-closed rule).
- `cba-log.md`: cont. 54 with the gate grep.
- `docs/deferred-backlog.md`: item 5 → ✅ DONE.
- `docs/plans/2026-11-02-card-controls.md` + README row.
- Merge only when you say "merge <PR>" and CI is green.
