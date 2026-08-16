# URL Shortener

A URL-shortening REST service built with Java 21, Spring Boot 3, PostgreSQL, Redis,
and Flyway — built for the "AI-Assisted Software Engineering" interview assignment.

> **This is not an AI-powered URL shortener.** There's no LLM call, agent, or model
> inference anywhere in the running application. "AI-assisted" describes how the
> *code was built* — AI tools accelerated implementation, debugging, and
> documentation, with a human reviewing, testing, and owning every change. See
> [AI-assisted development approach](#ai-assisted-development-approach) below.

```bash
git clone https://github.com/jobsemailrks-prog/ai-assisted-url-shortener.git
cd ai-assisted-url-shortener
docker-compose up --build
```

## What this is

A working URL shortener: create a short link, redirect through it, see click
analytics. Base62-encoded sequential IDs for short codes, Redis as a cache-aside
layer in front of Postgres, async click recording so analytics never slows down a
redirect, and Flyway-managed schema migrations.

## Why these architecture choices

- **Base62(sequential ID) instead of random codes.** Simpler — uniqueness comes free
  from the database's auto-increment, no collision-retry loop needed. The trade-off:
  short codes are enumerable (`id=1` → `1`, `id=2` → `2`, ...). Acceptable for a
  prototype where links aren't meant to be private/unguessable; would need to change
  if that assumption ever shifted.
- **Cache-aside, not cache-through, for Redis.** The app keeps working — just with
  higher latency, not broken correctness — if Redis is down. A cache-through design
  would make Redis a single point of failure for every redirect.
- **Async click recording, isolated from the redirect response.** A user must never
  see a failed or slow redirect because analytics logging had a problem. The
  trade-off: click counts are eventually consistent, not transactional — acceptable
  for analytics, would not be acceptable for anything billing-related.
- **`ddl-auto: validate` + Flyway, not `ddl-auto: update`.** Flyway owns the schema
  as the single source of truth; Hibernate only confirms the entity mappings agree
  with it at startup and fails fast on drift, instead of silently auto-altering
  tables. This was a real bug fixed during review — see
  [AI-assisted development approach](#ai-assisted-development-approach).

Full detail and the complete trade-off table: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/urls` | Liveness check (process is up; does **not** check DB/Redis) |
| `POST` | `/api/urls` | Create a short URL. Body: `{"originalUrl": "...", "expiresAt": "...", "ttlSeconds": ...}` (`expiresAt`/`ttlSeconds` optional) |
| `GET` | `/{code}` | Redirect to the original URL (`302 Found`) |
| `GET` | `/api/urls/{code}/analytics` | Total clicks + last-clicked timestamp |

### Example usage

```bash
# Create a short URL
curl -i -X POST http://localhost:8080/api/urls \
  -H "Content-Type: application/json" \
  -d '{"originalUrl": "https://example.com/some/very/long/path"}'

# → 201 Created
# {"shortCode":"1","shortUrl":"http://localhost:8080/1","originalUrl":"https://example.com/some/very/long/path","createdAt":"...","expiresAt":null,"active":true}

# Follow the redirect
curl -i http://localhost:8080/1
# → 302 Found, Location: https://example.com/some/very/long/path

# Check analytics
curl http://localhost:8080/api/urls/1/analytics
# → {"shortCode":"1","originalUrl":"...","totalClicks":1,"lastClickedAt":"...","createdAt":"..."}

# Malformed input is rejected before it reaches the database
curl -i -X POST http://localhost:8080/api/urls \
  -H "Content-Type: application/json" \
  -d '{"originalUrl": "not-a-url"}'
# → 400 Bad Request
```

## Running it

**Docker Compose (recommended):**
```bash
docker-compose up --build
```
Starts Postgres, Redis, and the app together; the app waits on both dependencies'
healthchecks before starting. API available at `http://localhost:8080`.

**Locally without Docker:**
```bash
# Start Postgres and Redis yourself, then:
mvn spring-boot:run
```
Defaults: Postgres at `localhost:5432` (db `urlshortener`, user `app_user`,
password `app_password`), Redis at `localhost:6379`. Override with
`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`,
`SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT` — see `docker-compose.yml` for
the exact set used in the containerized setup.

## Testing

```bash
mvn clean verify
```

**Test count: 24 total** (Base62Encoder: 3, UrlShortenerService: 12, UrlShortenerController: 6,
RedirectController: 3). **All 24 passed in a real local run**
with `mvn test` in this workspace (Java 21.0.3, macOS, `BUILD SUCCESS`).

| Test class | Covers |
|---|---|
| `Base62EncoderTest` | Encode/decode round-trip, zero/negative IDs, invalid characters |
| `UrlShortenerServiceTest` | Cache hit/miss, inactive/expired links, TTL calculation, click recording, analytics aggregation |
| `UrlShortenerControllerTest` | Liveness check, create (valid/blank/malformed), analytics (found/not-found) |
| `RedirectControllerTest` | Valid redirect (302 + Location header + click recorded), unknown code (404), expired code (404) |

End-to-end manual verification via `docker-compose up --build` + `curl`: create →
redirect → analytics, confirmed working with real Postgres/Redis, not just mocks.

## AI-assisted development approach

Built using **GitHub Copilot (in VS Code)**, **Claude**, **ChatGPT**, and **Gemini**,
in the workflow the assignment brief asks for: AI accelerates within a task, the
engineer reviews, tests, and owns every change that ships.

Two concrete examples of that in practice:

- **Base62 encoding**: AI proposed the encode/decode algorithm and an initial test
  set. Engineer review added the boundary cases that were missing (zero, negative
  IDs, invalid characters) and verified the round-trip independently before
  accepting it.
- **A real production-style bug, found and fixed live**: the app failed to start
  with a Hibernate schema-validation error — the database was missing a
  `click_count` column the JPA entity expected. Diagnosed via Copilot in VS Code
  (container logs → schema inspection → entity/migration comparison), fixed with a
  new Flyway migration, and verified with a clean `docker-compose down -v && up
  --build`. Two further review passes (with Claude) caught an information-disclosure
  bug in the generic exception handler and a CI workflow targeting the wrong Java
  version — both fixed and re-verified with a real local build.

Full task-by-task trace — including AI suggestions that were **rejected**, not just
accepted — is in [`docs/AI_TRACEABILITY.md`](docs/AI_TRACEABILITY.md). The three
required engineering scenarios (greenfield / brownfield / ambiguous requirement) are
in [`docs/SCENARIOS.md`](docs/SCENARIOS.md).

## Production considerations (not done here, and why)

This is a 2-3 day assignment prototype, not a production deployment. Scoped out
deliberately, not overlooked:

- **Rate limiting** on `POST /api/urls` and `GET /{code}` — the clearest gap against
  "reliability features" as literally stated in the brief. No protection against a
  scripted client creating unlimited links or hammering the redirect endpoint.
- **Circuit breaker around Redis** — a Redis timeout currently propagates as a
  slower/failed request rather than gracefully degrading straight to Postgres.
- **Custom alias support** — short codes are always system-generated.
- **A `DELETE`/deactivate endpoint** — the `active` column exists and is checked on
  every read, but nothing currently sets it to `false`.
- **IP hashing in `click_events`** — raw IP addresses are stored today; hashing them
  would close an easy privacy gap before this went anywhere near real user traffic.
- **Horizontal scaling / multi-instance concerns** — single-instance assumption
  throughout; no distributed cache invalidation or coordination.

Full reasoning behind what was and wasn't in scope: [`docs/SCENARIOS.md`](docs/SCENARIOS.md)
Scenario 3, and the complete list with rationale in
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md#known-limitations-intentionally-not-fixed-in-this-pass).

## Documentation index

| Doc | Contents |
|---|---|
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Components, diagram, control flow, trade-off table, limitations |
| [`docs/SCENARIOS.md`](docs/SCENARIOS.md) | Greenfield / brownfield / ambiguous-requirement walkthroughs |
| [`docs/AI_TRACEABILITY.md`](docs/AI_TRACEABILITY.md) | Task-by-task: prompt → AI output → engineer review → validation |
| [`docs/FINAL_SUMMARY.md`](docs/FINAL_SUMMARY.md) | Closing plan, risks, assumptions, limitations |
