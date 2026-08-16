# Final Engineering Summary

This document ties together the deliverables scattered across `README.md`,
`docs/ARCHITECTURE.md`, `docs/SCENARIOS.md`, and `docs/AI_TRACEABILITY.md` into
the single closing summary the assignment brief asks for: plan and rationale,
artifacts produced, risks/trade-offs/validation, assumptions, and limitations.

## Plan and rationale

The brief's core requirement — "URL shortener with core APIs, analytics, and
reliability features" — was broken into a build phase and a review phase, tracked
separately because they demonstrate different things:

1. **Greenfield build** (`docs/SCENARIOS.md` Scenario 1): the initial system —
   schema, entities, Base62 short codes, cache-aside Redis, async analytics,
   REST layer, Docker Compose. AI accelerated the mechanical parts (encoding
   math, entity boilerplate, Dockerfile); the engineer made and owns the
   structural decisions (sequential-ID codes over random, cache-aside over
   cache-through, atomic click-count updates over read-modify-write).

2. **Review and brownfield fix pass** (`docs/SCENARIOS.md` Scenario 2): a
   deliberate second look at "working" code surfaced two real defects — a dead
   Flyway migration masked by Hibernate auto-DDL, and an exception handler
   leaking internal error detail to clients. Both were fixed, and both fixes are
   verified against the actual build output, not just the diff.

3. **Ambiguity resolution** (`docs/SCENARIOS.md` Scenario 3): "reliability
   features" was never defined by the brief. Rather than guess at scope, the
   ambiguity was resolved by mapping it to the system's actual failure modes
   (Redis outage, analytics-write failure, abusive traffic) and building against
   the ones that mattered most for a system serving live redirects, while
   explicitly naming the ones left out (rate limiting, circuit breaking) as
   scoped-out rather than silently missing.

Throughout, the operating principle was the one stated in the brief: AI
accelerates within a task; the engineer decides what's correct, what's rejected,
and what ships. `docs/AI_TRACEABILITY.md` is the concrete evidence trail for that
— including two outright-rejected AI suggestions (`ddl-auto: none`, and deleting
`ex.getMessage()` entirely rather than just not exposing it to clients).

## Artifacts produced

| Artifact | Location |
|---|---|
| Working service (Spring Boot 3.3.3, Java 21) | `src/main/java/...` |
| Schema migrations (Flyway) | `src/main/resources/db/migration/V1__init_schema.sql`, `V2__add_click_count_column.sql` |
| Unit tests (14 total: encoder, service, controller) | `src/test/java/...` |
| Docker Compose (app + Postgres + Redis) | `docker-compose.yml`, `Dockerfile` |
| CI workflow (build + test on push/PR) | `.github/workflows/ci.yml` |
| Architecture overview | `docs/ARCHITECTURE.md` |
| Three required scenarios | `docs/SCENARIOS.md` |
| AI-assisted execution log | `docs/AI_TRACEABILITY.md` |
| Setup instructions | `README.md` |
| This summary | `docs/FINAL_SUMMARY.md` |

## Validation performed

- `mvn clean verify`: **14/14 tests passing** in the last real local run confirmed
  before this pass (Java 21.0.3, `BUILD SUCCESS`). **10 additional tests were added
  in this pass** (`RedirectControllerTest` + TTL/validation/analytics-404 cases) and
  have **not yet been run** — run `mvn clean verify` and update this line with the
  real result before treating the suite as fully verified. Total test count in the
  codebase is 24; only 14 have a confirmed passing run behind them as of this
  writing.
- Live end-to-end verification via `docker-compose up --build`:
  - `POST /api/urls` → `201 Created` with correct payload
  - `GET /{code}` → `302 Found` with correct `Location` header
  - `GET /api/urls/{code}/analytics` → accurate click counts across repeated
    hits from two different clients (curl and browser)
  - `GET /api/urls` (liveness) → `200 OK`
- `jar tf target/*.jar | grep flyway` used as a concrete, checkable confirmation
  that the Flyway dependency fix actually landed in the built artifact, rather
  than trusting the pom.xml diff alone.

## Risks and trade-offs (see `docs/ARCHITECTURE.md` for full detail)

| Risk | Why it was accepted |
|---|---|
| Short codes are enumerable (sequential ID → Base62) | Simpler, no collision handling needed; acceptable for a prototype where links aren't meant to be private/unguessable — would need to change if that assumption ever shifts |
| No rate limiting on create/redirect endpoints | Time-boxed scope; the failure modes that would silently break correctness (Redis down, analytics write failure) were prioritized over the failure mode that "only" enables abuse |
| Click counts are eventually consistent, not transactional | Analytics must never block or fail a redirect; a crash in the narrow async window between response and write loses one click event, which is an acceptable trade for a system where click count isn't billing-critical |
| Raw IP addresses stored in `click_events` | Deferred rather than solved — flagged explicitly as a privacy gap rather than left undocumented |

## Assumptions

- Short URLs do not need to be cryptographically unguessable (informs the
  Base62-sequential-ID decision).
- Click-count accuracy can tolerate eventual consistency; it is not used for
  anything transactional (billing, quotas).
- Single-region, single-instance deployment for this prototype's scope — no
  distributed-cache-invalidation or multi-node coordination concerns addressed.
- The assignment's 2-3 day scope means some reliability features (rate
  limiting, circuit breaking) are explicitly out of scope rather than
  incomplete oversights.

## Limitations

See `docs/ARCHITECTURE.md#known-limitations-intentionally-not-fixed-in-this-pass`
for the complete, current list. Summarized: no rate limiting, no Redis circuit
breaker, no custom aliases, no link-deactivation endpoint, unhashed IPs in
analytics, and GitHub Actions CI has been pushed but not yet confirmed with a
live green run (distinct from the local `mvn verify` runs, which are confirmed).

## What would change with more time

In rough priority order: rate limiting on both endpoints (the clearest gap
against "reliability features" as literally stated in the brief), a circuit
breaker or timeout-with-fallback around the Redis client, a `DELETE
/api/urls/{code}` endpoint to actually exercise the `active` flag the schema
already supports, and IP hashing in `click_events` to close the privacy gap
before this went anywhere near real user traffic.
