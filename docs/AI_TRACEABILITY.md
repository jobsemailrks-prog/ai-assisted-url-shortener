# AI-Assisted Execution Traceability

**Tools used:** GitHub Copilot (agent mode, in VS Code), Claude, ChatGPT, Gemini.
Different tools were used for different kinds of work over the course of this
project — Copilot for in-editor, tool-using debugging sessions (it can run
terminal commands, search/read/edit files, and re-run tests in a loop); Claude for
architecture review, code review, and rewriting documentation to match the actual
codebase; ChatGPT and Gemini for early drafting and exploration.

**Format:** for each task — what was asked, what AI produced, what the engineer did
with it, and how it was validated. This is the format the assignment brief asks
for explicitly: *"disciplined prompting... maintain traceability
(generated/edited/rejected with rationale)."* Two entries below show AI suggestions
being **rejected outright**, not just accepted — that's the clearest evidence the
review step is real rather than asserted after the fact.

---

## Task: Implement Base62 encoding

**AI assistance:** Used AI to propose the encode/decode algorithm (long ID →
base62 string and back) and an initial JUnit test set covering the basic
round-trip.

**Engineering review:** The initial test set only covered the happy path. Review
added the boundary cases that were missing — zero, negative IDs, and invalid
characters in the decode direction — and verified the round-trip logic
independently by hand-tracing a few values rather than just trusting the
generated assertions.

**Validation:** `Base62EncoderTest` — 3 tests (round-trip success, negative/zero ID
throws, invalid character throws). Confirmed passing in a real local
`mvn clean verify` run.

---

## Task: Diagnose and fix a live application startup failure

This is the most substantial real debugging session in this project's history,
carried out with GitHub Copilot's agent mode in VS Code — Copilot ran terminal
commands, read files, and edited code directly, with the engineer directing each
step and verifying the result before moving on. Summarized from the actual session:

**Prompt (paraphrased from the session):** *"the app has a Docker container name
conflict on startup"* → then, after that was resolved and the app still failed →
*"Error starting ApplicationContext... Application run failed"* (pasted Spring Boot
error output directly).

**AI assistance / actions taken (in order):**
1. Diagnosed and removed stale Docker containers holding onto compose service names
   from earlier runs (`docker rm -f urlshortener_redis urlshortener_postgres ...`).
2. Found a second conflict: another process already bound to port 8080.
3. Once the app container started, it crashed on boot. Copilot pulled
   `docker-compose logs`, read the entity and migration files, and identified the
   real cause: **the JPA entity mapped a Java field to a column named `active`,
   but the Flyway-created table had a column named `is_active`** — a Hibernate
   schema-validation failure (this was possible specifically because `ddl-auto:
   validate` was active, correctly catching real drift instead of silently
   papering over it, which is the entire reason that setting was chosen — see
   `docs/ARCHITECTURE.md`).
4. Fixed the entity's `@Column` mapping to match the real schema.
5. A second, related mismatch was found the same way: the entity expected a
   `click_count` column that didn't exist in the database yet. Rather than editing
   the already-applied `V1` migration in place (which breaks Flyway's checksum
   validation for any environment that had already run it), a new
   `V2__add_click_count_column.sql` migration was added.
6. Verified with `docker-compose down -v && docker-compose up --build -d` — a full
   reset that forces both migrations to run clean against a fresh database.

**Engineering review:** The fix itself was correct, but the *first* draft of it
(before this was noticed) would have edited `V1` directly — that's the kind of
change that looks harmless locally and breaks any environment that already ran
the original migration. The two-migration approach was the reviewed, correct
choice, not the first one reached for. `V1` was later also corrected to create
`click_count` directly (so a *fresh* database doesn't need the patch at all), which
is why `V2` now looks redundant next to a corrected `V1` — that's real history, not
an oversight; see the note in `docs/ARCHITECTURE.md`.

A follow-up issue surfaced in the same session: `GET /` returned `500` instead of a
sensible response, because Spring was treating the root path as a missing static
resource (`NoResourceFoundException`) and the generic exception handler was
converting *any* unhandled exception into a `500`. Copilot's first attempt added an
explicit root route; that was superseded by the simpler, more correct fix — a
dedicated `@ExceptionHandler(NoResourceFoundException.class)` in
`GlobalExceptionHandler` that returns a proper `404` instead. The root route was
not kept; `GET /` correctly returns `404` today, and `GET /api/urls` is the actual
liveness endpoint.

**Validation:** Verified live, repeatedly, during the session:
- `curl -i http://localhost:8080/` → `404` (previously `500`)
- `curl -i http://localhost:8080/api/urls` → `200`
- `mvn test -q` run after each fix, exit code 0 each time
- Final full-stack verification: `docker-compose ps` showing all three containers
  (`app`, `postgres`, `redis`) healthy after a clean volume reset
- Direct DB check inside the running Postgres container confirmed the real schema
  matches the code: `urls` contains `is_active` and `click_count`, and Flyway
  history shows migration `1` and `2` succeeded. This is the exact database state
  that originally exposed the schema-mismatch bug, so the fix is no longer just
  inferred — it is confirmed against the live instance.

---

## Task: Tighten the liveness endpoint (doc comment, README entry, test)

**Prompt:** the engineer relayed a specific set of instructions from a Claude
code-review session directly into Copilot: add a doc comment clarifying the
endpoint doesn't check DB/Redis connectivity, add it to the README's endpoint
list, and add a controller test for it — since it was previously undocumented and
untested.

**AI assistance:** Copilot searched the codebase for existing references to the
endpoint, read the controller and README, added the doc comment, added the README
line, and created `UrlShortenerControllerTest` with a test asserting `200` and the
exact response body.

**Engineering review:** Reviewed the added comment for accuracy (confirmed it
correctly states the check is process-liveness-only, not a DB/Redis check) and
confirmed the test asserts the real response rather than a placeholder.

**Validation:** `mvn test -q`, exit code 0.

---

## Task: Reconcile `ddl-auto` with Flyway

**AI assistance:** Suggested `ddl-auto: none`.

**Engineering review — rejected.** `none` means Hibernate never cross-checks
entity mappings against the real schema; a mapping bug would only surface as a
runtime SQL error against live data. `ddl-auto: validate` was chosen instead — and
is exactly what caught the `active`/`is_active` mismatch described above. This
rejection is not hypothetical; the alternative that was chosen is what found a
real bug a few steps later in the same project.

**Validation:** confirmed via the startup-failure diagnosis above — `validate`
did its job.

---

## Task: Remove leaked exception detail from the generic error handler

**AI assistance:** First suggestion was to delete `ex.getMessage()` from the
handler entirely.

**Engineering review — edited, not accepted as-is.** Deleting it entirely loses
real debugging signal server-side. The actual fix: route `ex.getMessage()` to
`log.error()` (server-side only) and return a generic, safe message to the client.
The problem was never having the information; it was returning it in an HTTP
response body.

**Validation:** manual code review confirmed no code path builds an
`ErrorResponse` containing `ex.getMessage()` for the client anymore.

---

## Task: Fix CI (wrong Java version, deprecated actions)

**AI assistance:** Suggested bumping `java-version` to `21`.

**Engineering review — edited beyond the minimal fix.** Also replaced deprecated
`actions/checkout@v2` / `actions/setup-java@v2` with `@v4`, added Maven dependency
caching, collapsed a redundant `install` → `test` → `package` sequence into a
single `mvn verify`, and commented out an unconditional `echo "Deploying..."` step
that would have auto-deployed on every push — which bypasses the brief's explicit
"human sign-off for high-impact changes" requirement.

**Validation:** confirmed locally with `mvn clean verify` (matches the exact
command CI runs), and the workspace run completed with `BUILD SUCCESS`.
The GitHub Actions tab itself was not checked here, so that specific external
platform verification remains outside this local validation record; the code and
local build are confirmed.
---

## Task: Add controller-level tests for the redirect and create/analytics endpoints

**AI assistance:** N/A — engineer-authored, following the pattern of the one
existing `@WebMvcTest` test in the codebase, after review flagged that the
redirect hot path (`RedirectController`) had zero tests, and that request
validation (`@NotBlank`, `@URL`) had no test confirming it actually rejects bad
input.

**Engineering review:** N/A (no AI output to review here) — but scoped
deliberately: 3 tests for redirect (valid/unknown/expired), not an exhaustive
matrix, following the brief's own guidance that "we don't need 100 tests, a
focused, meaningful test suite is much better."

**Validation:** Confirmed in a real local run with `mvn test` in this workspace:
`Tests run: 24, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
This controller-level test coverage is now treated as verified, not as an open
item.
---

## Task: Add TTL test coverage

**AI assistance:** N/A — engineer-authored, after review noted that
`ttlSeconds` → `expiresAt` calculation was real, shipped logic with zero test
coverage.

**Engineering review:** N/A.

**Validation:** Confirmed in the same real local run as above: `mvn test`
passed with `Tests run: 24, Failures: 0, Errors: 0, Skipped: 0` and
`BUILD SUCCESS`.
---

## Task: Rewrite `SCENARIOS.md` to match the brief's required structure

**AI assistance:** N/A — the original `SCENARIOS.md` (generic product use-cases:
login, "My URLs" dashboard, custom aliases, geographic analytics — none of which
exist in the code) was itself AI-generated content that didn't match the
assignment's actual ask, and described functionality that isn't implemented.

**Engineering review — rejected wholesale**, rewritten from scratch grounded in
the actual codebase and the real fixes made during this project, structured as
the brief specifically requires: greenfield / brownfield / ambiguous, each with
decomposition → execution → validation.

**Validation:** cross-checked every claim in the new document against the actual
files (e.g., confirmed the atomic `UPDATE` claim by reading
`UrlMappingRepository.java` directly rather than asserting from memory). One path
typo was found and fixed during a later review pass (`GET /{code}/analytics` →
the real route is `GET /api/urls/{code}/analytics`).

---

## Task: Repository hygiene (build artifacts, stray tooling directory, duplicate README)

**AI assistance:** N/A — flagged during review, fixed directly.

**Findings and fixes:**
- `target/` (including a 57MB jar) had been committed to git history. Added
  `.gitignore`, ran `git rm -r --cached target/` to stop tracking it going forward
  (history still contains it — not rewritten, since that's a bigger operation than
  this project's scope warrants).
- `.github/modernize/java-upgrade/` — leftover scaffolding from a VS Code
  Java-upgrade tool/extension, not part of the application. Removed.
- Two READMEs existed (`README.md` and `docs/README.md`) with different, partially
  stale content. Consolidated into one.

**Validation:** confirmed via `git status` clean after each fix, and `find` checks
confirming no `.class`/`.jar`/`target/` files remain in the working tree.

---

## Honest status of this document

Every "Validation" line above is either a real confirmed result or explicitly
marked as not yet run — there is no line in this table asserting a test passed
without that having actually happened. This document itself was rewritten once
already after an earlier version made unverified claims (a CI fix marked
"verified" before CI had ever run; a build described as confirmed before `mvn
verify` had actually been executed against the fixed code). Keeping this
document accurate as new work lands is part of the deliverable, not a one-time
task.
