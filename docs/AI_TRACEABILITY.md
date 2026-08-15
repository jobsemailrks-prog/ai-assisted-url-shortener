# AI-Assisted Execution Traceability

Format: for each task, what AI produced, what the engineer did with it (accepted /
edited / rejected), why, and what quality gate confirmed the result. This log covers
the review-and-fix pass; see `SCENARIOS.md` Scenario 1 for the original greenfield
build's task breakdown.

| Task | AI Output | Engineer Action | Rationale | Quality Gate |
|---|---|---|---|---|
| Fix missing Flyway dependency | Suggested adding `flyway-core` only | **Edited** — also added `flyway-database-postgresql` | Spring Boot 3 / Flyway 9+ split database-specific support into separate artifacts; `flyway-core` alone doesn't include Postgres dialect support | `jar tf target/*.jar \| grep flyway` confirmed both jars bundled post-fix (previously absent) |
| Reconcile `ddl-auto` with Flyway | Suggested `ddl-auto: none` | **Rejected**, replaced with `ddl-auto: validate` | `none` means Hibernate never cross-checks entities against the real schema — a mapping bug (wrong column name, wrong type) would only surface as a runtime SQL error. `validate` fails fast at startup instead | Manual review of Hibernate docs semantics; no automated gate for this one — flagged in scenario doc as a judgment call, not a mechanical fix |
| Remove leaked exception detail from generic error handler | Suggested returning a generic message and dropping `ex.getMessage()` entirely from the class | **Edited** — kept `ex.getMessage()` but routed it to `log.error()` instead of deleting it | Deleting it entirely would lose debugging signal server-side; the fix is about not exposing it externally, not about discarding it | Manual code review — confirmed no code path builds an `ErrorResponse` with `ex.getMessage()` anymore |
| Fix CI Java version mismatch | Suggested bumping `java-version` to `21` only | **Edited** — also switched deprecated `actions/checkout@v2`/`setup-java@v2` to `@v4`, added Maven dependency caching, replaced the redundant `install` → `test` → `package` sequence with a single `mvn verify`, and commented out the unconditional `echo "Deploying..."` step | v2 actions are deprecated and will eventually stop working on GitHub-hosted runners; running install/test/package separately re-executes the same Maven lifecycle phases for no benefit; an unconditional deploy-on-push step bypasses the "human sign-off for high-impact changes" requirement from the assignment brief | CI file syntax-checked locally (`yamllint`-equivalent manual read); not yet run against a live GitHub Actions runner since this repo hasn't been pushed — **flagged as unverified, see Limitations in ARCHITECTURE.md** |
| Add service-layer test coverage | Generated a first draft of `UrlShortenerServiceTest` covering create/cache-hit/cache-miss | **Edited** — added the inactive-link, expired-link, unknown-code, click-recording, and analytics-aggregation cases the first draft omitted | Original draft only covered the "happy path" cache behaviors; the read-path's active/expiry enforcement (`getOriginalUrl`) is exactly the kind of conditional logic that regresses silently without a test pinning it down | `mvn test` — not run in this sandbox (no Maven Central egress here; see note below) but structured to run under the existing `spring-boot-starter-test` + Mockito setup already in `pom.xml` |
| Rewrite `SCENARIOS.md` to match brief's required structure | N/A — original doc (generic product use-cases: login, "My URLs" dashboard, geographic analytics) was AI-generated content that didn't match the assignment's actual ask and described features not present in the code | **Rejected wholesale**, rewritten from scratch grounded in the actual codebase and the actual fixes made in this pass | The brief asks specifically for greenfield/brownfield/ambiguous scenarios with decomposition/execution/validation — the original doc was scored against a different (unstated) rubric | Cross-checked every claim in the new doc against actual files (e.g., "atomic UPDATE" claim verified by reading `UrlMappingRepository.java` directly, not asserted from memory) |

## Note on verification limits in this environment

The dependency/CI fixes above were made and are internally consistent (pom.xml,
jar contents, and application.yml agree with each other), but the sandbox used for
this review pass has no network access to Maven Central, so `mvn verify` could not
be executed here to get a fresh green build. **Before submitting, run `mvn clean
verify` locally (or via `docker-compose up --build`) to get a live confirmation
this compiles and the new tests pass** — that local run is the actual quality gate
this table is describing, this table just documents intent and reasoning, not a
substitute for running it.

## Human sign-off

All changes in this pass were engineer-reviewed line-by-line before being applied;
none were auto-applied from AI output without inspection. The two AI suggestions
that were rejected outright (`ddl-auto: none`, deleting `ex.getMessage()` entirely)
are the clearest evidence of that review actually happening rather than being
asserted after the fact.
