# Engineering Scenarios

This document walks through the three scenarios required by the assignment brief:
**greenfield** (new system), **brownfield** (enhancement/fix on an existing codebase),
and **ambiguous** (an underspecified requirement that needed clarification before it
could be implemented). Each section shows decomposition → execution → validation, and
is written to reflect what actually happened, not an idealized version of it.

---

## Scenario 1 — Greenfield: Core URL Shortening Service

**Requirement (as given):** Build a URL shortener from scratch with core APIs,
analytics, and reliability features.

### 1. Requirement understanding
The one-line requirement hides several implicit decisions that had to be made
explicit before writing code:
- How are short codes generated — random, or derived from a sequential ID?
- Where does uniqueness get enforced — application layer or DB constraint?
- Is a redirect a cache-first or DB-first read?
- What does "analytics" mean at minimum — a click counter, or per-click event data?

These were resolved as: **Base62-encoded sequential IDs** (simple, no collision
handling needed, but see the *enumerable short codes* trade-off in
`ARCHITECTURE.md`), **DB-level unique constraint on `short_code`** as the source of
truth, **Redis cache-aside** on the read path, and **both** an aggregate click
counter (cheap reads for "how popular is this link") and a per-event `click_events`
table (supports later time-series/referrer analytics).

### 2. Task decomposition
| # | Task | Depends on |
|---|------|------------|
| 1 | Schema design + Flyway migration (`urls`, `click_events`) | — |
| 2 | `Base62Encoder` (encode/decode, edge cases: 0, negative, invalid chars) | — |
| 3 | JPA entities (`UrlMapping`, `ClickEvent`) | 1 |
| 4 | Repositories (`findByShortCode`, atomic `incrementClickCount`) | 3 |
| 5 | `UrlShortenerService`: create, cache-aside resolve, async click recording, analytics read | 2, 4 |
| 6 | REST controllers (`POST /api/urls`, `GET /{code}`, `GET /{code}/analytics`) | 5 |
| 7 | Validation + `GlobalExceptionHandler` | 6 |
| 8 | Dockerfile + docker-compose (Postgres + Redis + app) | 5 |
| 9 | Unit tests | 2, 5 |

### 3. AI-assisted execution
AI was used to draft the mechanical/boilerplate-heavy pieces (Base62 conversion
math, JPA entity getters/setters, DTO validation annotations, Dockerfile multi-stage
build) and to propose the cache-aside pattern shape. Every generated piece was
reviewed against a specific question: *does this hold up under concurrent access,
partial failure, or bad input?* Two concrete review outcomes from this phase:
- **Accepted as-is:** `Base62Encoder` — pure function, easy to reason about,
  covered by unit tests for the zero/negative/invalid-character edge cases.
- **Edited:** the AI's first pass at `incrementClickCount` used a read-then-write
  (`load entity, count++, save`) pattern, which loses updates under concurrent
  redirects of the same popular link. It was rewritten as a single atomic
  `UPDATE ... SET click_count = click_count + 1` JPQL query (see
  `UrlMappingRepository`).

### 4. Validation
- Unit tests for `Base62Encoder` boundary conditions.
- Manual verification: `docker-compose up`, create a URL via `POST /api/urls`,
  confirm redirect via `GET /{code}`, confirm `click_count` increments and
  `click_events` rows appear after redirect.
- Quality gate: `mvn verify` (compile + test) in CI.

---

## Scenario 2 — Brownfield: Fixing Schema Management and an Information-Disclosure Bug

**Trigger:** a review pass on the completed prototype (see `docs/AI_TRACEABILITY.md`
for the full list) surfaced two defects in already-"working" code — the kind of
brownfield work that shows up constantly in real codebases: the code runs, tests
pass, and it's still wrong.

### 1. Codebase reasoning
**Defect A — dead migration, live auto-DDL.** `application.yml` had
`spring.flyway.enabled: true` and a `V1__init_schema.sql` migration existed, but
`flyway-core` was never added to `pom.xml`. Spring Boot's Flyway autoconfiguration
is conditional on the Flyway classes being present on the classpath — with the
dependency missing, it silently no-ops. Meanwhile `ddl-auto: update` was active, so
Hibernate was actually creating the schema at runtime by reflecting the JPA
entities. Net effect: the migration file was dead code, and the "real" schema was
whatever Hibernate happened to infer — no migration history, no guaranteed
match between entity mappings and the SQL DDL. This is invisible in local dev
(the app "just works") and only shows up as schema drift or a failed deploy later.

**Defect B — internal error detail leaked to clients.**
`GlobalExceptionHandler.handleGenericException` built its response message as
`"An unexpected error occurred: " + ex.getMessage()`, which forwards raw exception
text — stack details, SQL fragments, internal class/field names depending on the
failure — straight into the HTTP response body.

### 2. Task decomposition
| # | Task |
|---|------|
| 1 | Add `flyway-core` + `flyway-database-postgresql` to `pom.xml` |
| 2 | Change `ddl-auto` from `update` to `validate`, add `baseline-on-migrate: true` |
| 3 | Strip `ex.getMessage()` from the generic exception handler's client-facing response; log it server-side instead |
| 4 | Fix CI (`ci.yml` was targeting Java 11 against a Java 21 `pom.xml` — would fail on first build) |

### 3. AI-assisted execution and review
This is where "engineer owns correctness" mattered most. An AI-assisted first pass
suggested simply flipping `ddl-auto` to `none` and leaving Flyway to do everything —
**rejected**, because `validate` is strictly safer during this transition: it makes
Hibernate actively confirm the entity mappings agree with the Flyway-managed schema
at startup and fail loudly on mismatch, instead of trusting Flyway blindly with no
cross-check. `validate` was chosen and is documented inline in `application.yml`
with the rationale, not just the value.

### 4. Validation
- Confirmed `flyway-core` is present in the built artifact (`jar tf ... | grep flyway`)
  where before it was absent — this was the concrete, checkable signal the bug was
  real, not theoretical.
- Confirmed the generic exception handler no longer contains `ex.getMessage()` in
  the client response path (log-only now).
- CI updated to Java 21 to match `pom.xml`; a broken CI file that would fail on
  first push is itself a quality-gate defect, not just a cosmetic one.

---

## Scenario 3 — Ambiguous Requirement: "Reliability Features"

**Requirement (as given):** the assignment brief asks for "analytics, and reliability
features" without defining what qualifies as a reliability feature for a system at
this scale. This is a genuinely underspecified requirement — reasonable
implementations range from "the redirect endpoint doesn't 500 on bad input" to
"circuit breakers, rate limiting, and multi-region failover."

### 1. Normalizing the ambiguity
Rather than guessing at the top end of that range (over-engineering a link
shortener with infrastructure it doesn't need) or the bottom end (calling basic
error handling "reliability" and stopping there), the requirement was narrowed
using the system's actual failure modes:
- What happens if Redis is down? → falls back to Postgres read (already true via
  cache-aside; **not** true of a cache-through design, which was considered and
  rejected for this reason).
- What happens if the DB write for a click event fails? → must not fail the
  redirect itself, since a user waiting on a 302 should never see a 500 because
  analytics logging hiccuped. This is why click recording is `@Async` and wrapped
  independently from the redirect response.
- What happens under abusive/scripted traffic? → **currently unaddressed** — there
  is no rate limiting on `POST /api/urls` or on the redirect endpoint. This is
  called out explicitly as a known gap rather than silently left out (see
  `ARCHITECTURE.md` limitations section), since claiming "reliability features" as
  complete without it would be an overstatement of what was actually built.

### 2. Decomposition of what was in-scope for this pass
| # | Task | Status |
|---|------|--------|
| 1 | Cache-aside fallback so Redis outage degrades to Postgres, not failure | Done |
| 2 | Async, isolated click recording so analytics failures don't fail redirects | Done |
| 3 | Input validation (`@NotBlank`, `@URL`) rejecting malformed requests before they reach the DB | Done |
| 4 | Rate limiting on create/redirect endpoints | **Not done — documented as a limitation** |
| 5 | Circuit breaker around Redis calls (currently a direct call; a Redis timeout on read would propagate as a request failure rather than a graceful DB fallback) | **Not done — documented as a limitation** |

### 3. Validation and the decision to stop here
Items 4 and 5 were consciously deprioritized rather than silently dropped, given
the 2-3 day scope of the assignment and that they add real complexity
(Resilience4j config, tuning limits, testing the fallback paths) for a prototype
that isn't under production traffic. The engineering judgment being demonstrated
here isn't "we built everything reliability-related" — it's knowing which
reliability gaps are acceptable to ship with clearly documented, and which
(cache fallback, non-blocking analytics) were non-negotiable for a system that
redirects real user traffic.
