# URL Shortener

A URL shortener service built with Spring Boot 3, Java 21, PostgreSQL, and Redis, for
the AI-Assisted Software Engineering interview assignment.

## Features

- `GET /api/urls` — basic liveness check
- `POST /api/urls` — create a short URL, with optional `expiresAt` or `ttlSeconds`
- `GET /{code}` — redirect to the original URL (HTTP 302)
- `GET /api/urls/{code}/analytics` — total clicks, last-clicked timestamp
- Redis cache-aside on the redirect path; Postgres remains the source of truth
- Flyway-managed schema (see `src/main/resources/db/migration`)
- Async click recording — analytics writes never block or fail a redirect

See `docs/ARCHITECTURE.md` for component/control-flow details and documented
trade-offs, `docs/SCENARIOS.md` for the greenfield/brownfield/ambiguous engineering
walkthroughs, and `docs/AI_TRACEABILITY.md` for the AI-assisted execution log.

## Prerequisites

- Docker + Docker Compose (recommended path below), **or**
- Java 21, Maven, a local PostgreSQL 16 instance, and a local Redis 7 instance

## Quick start (Docker Compose)

```bash
docker-compose up --build
```

This starts Postgres, Redis, and the app together, with the app waiting on both
dependencies' healthchecks before starting. The API is available at
`http://localhost:8080` once all three containers report healthy.

## Running locally without Docker

```bash
# Start Postgres and Redis yourself, then:
mvn spring-boot:run
```

By default the app expects Postgres at `localhost:5432` (db `urlshortener`, user
`app_user`, password `app_password`) and Redis at `localhost:6379`. Override with
environment variables (`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`,
`SPRING_DATASOURCE_PASSWORD`, `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT`) —
see `docker-compose.yml` for the exact set used in the containerized setup.

## Running tests

```bash
mvn test
```

> **Note on this repo's history:** the dependency and CI fixes described in
> `docs/AI_TRACEABILITY.md` were made in a sandboxed review environment without
> access to Maven Central, so `mvn verify` could not be run there to confirm a
> green build after those changes. Run it here, in an environment with normal
> internet access, before treating this as final.

## Example usage

```bash
# Create a short URL
curl -X POST http://localhost:8080/api/urls \
  -H "Content-Type: application/json" \
  -d '{"originalUrl": "https://example.com/some/very/long/path"}'

# Follow the redirect
curl -i http://localhost:8080/{code}

# Check analytics
curl http://localhost:8080/api/urls/{code}/analytics
```

## Known limitations

See `docs/ARCHITECTURE.md#known-limitations-intentionally-not-fixed-in-this-pass`
for the full list (no rate limiting, no custom aliases, no link deactivation
endpoint, enumerable short codes, unhashed IPs in click events). These are
documented gaps, not oversights — see `docs/SCENARIOS.md` Scenario 3 for the
reasoning behind what was and wasn't in scope.
