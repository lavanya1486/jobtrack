# JobTrack — résumé bullets and interview talking points

Every number below is measurable in this repository. The verification command is given for each, so
nothing has to be taken on faith — and nothing should be pasted into a résumé that you cannot
reproduce in an interview.

> **Rule used while writing these:** no invented metrics. There are no claims about users, traffic,
> latency percentiles, cost savings or "X% faster" figures, because this project has no production
> traffic to measure. The quantified parts are code-level facts: test counts, coverage, endpoint and
> table counts, bundle sizes.

---

## Project header

**JobTrack — Job Application Tracker with Analytics** · *personal full-stack project*
Java 21 · Spring Boot 3.5 · Spring Security · PostgreSQL 17 · Flyway · React 18 · Vite · Docker · GitHub Actions

One-line description:

> A multi-user job-search tracker with a status pipeline, interview scheduling, automated reminders
> and defensible analytics, built as a Spring Boot REST API (JWT + rotating refresh tokens) and a
> React SPA, containerised with Docker Compose and covered by 260 automated tests.

---

## The four strongest bullets

Pick these if you have room for one project block of 3–5 lines.

- **Designed and implemented a Spring Boot 3.5 / Java 21 REST API with 35 endpoints and 7 PostgreSQL
  tables**, enforcing per-user data isolation in every query and returning `404` (not `403`) for
  another user's records so the API cannot be used to enumerate ids.
- **Implemented stateless JWT authentication with rotating refresh tokens**: 30-minute HS256 access
  tokens, SHA-256-hashed refresh tokens with family ids, reuse detection that revokes an entire token
  family, and role checks in both the filter chain and `@PreAuthorize` — verified by 20 dedicated
  integration tests.
- **Built the analytics engine with one shared denominator** (submitted applications) for response,
  interview and offer rates, a cumulative interview funnel derived from an append-only status-history
  table, and gap-filled monthly trends, all aggregated in SQL and pinned by 16 integration tests
  against a hand-built dataset.
- **Wrote 189 backend tests (87 unit, 102 full-stack MockMvc integration tests against a real
  PostgreSQL via Testcontainers) and 71 React tests, reaching 83.6 % line coverage**, which surfaced
  eight real defects — including a stale persistence context that silently undid refresh-token
  revocation.

---

## Extended menu (choose by role)

### Security & authentication

- Implemented JWT issuing and validation with issuer/audience/type checks, 30-second clock skew and a
  fail-fast startup when the HMAC secret is shorter than 256 bits or missing in the `prod` profile.
- Designed refresh-token rotation with family revocation: a replayed token revokes every token in its
  family and forces re-authentication on all devices; password changes and admin role changes revoke
  the affected user's tokens as well.
- Made the `@Modifying` bulk revocation queries `flushAutomatically`/`clearAutomatically` after an
  integration test proved the persistence context was resurrecting revoked rows.
- Encoded authorisation in three layers (URL rules, `@PreAuthorize` on controllers *and* services,
  user-scoped repository queries) so moving a method cannot silently expose it.
- Stored passwords with BCrypt (strength 10) and returned one generic error for "unknown e-mail" and
  "wrong password" to prevent account enumeration.

### Backend & data

- Modelled the schema with explicit cascade rules (`ON DELETE CASCADE` for owned data, `SET NULL` for
  audit rows), `TIMESTAMPTZ` for instants and `DATE` for calendar values so deadlines never shift
  across time zones.
- Added a partial unique index on `(user_id, dedupe_key) WHERE completed_at IS NULL` to make reminder
  generation idempotent at the storage level, so overlapping scheduler runs or extra replicas cannot
  duplicate a reminder.
- Wrote functional indexes on `lower(company)`/`lower(job_title)` plus composite `(user_id, …)` indexes
  matching the list, filter and sort queries.
- Implemented server-side search, multi-value filters, whitelisted sort keys (no user input reaches a
  column name) and pagination with real totals; inverted date ranges return `400` instead of an empty
  page.
- Centralised error handling into one envelope (`status`, machine-readable `error` code, `message`,
  `fieldErrors`, `details`) shared by the MVC advice and the security filter chain, so a `401` from the
  JWT filter is indistinguishable in shape from a `400` from validation.
- Managed the schema with Flyway and `ddl-auto: validate`, so mapping drift fails at boot instead of
  altering production tables.

### Domain logic & scheduling

- Encoded the seven-status pipeline as a pure transition policy (37 unit-tested cases) returning the
  allowed targets in the error body, so the UI can render legal next moves.
- Built the reminder pipeline: deadline, next-action, interview-prep and unanswered-application
  follow-ups, reconciled in place when dates move and dropped when an application closes.
- Handled time zones end to end: date-only reminders become due at 09:00 in the owner's IANA zone,
  stored as UTC instants and returned with both `dueAt` and `dueAtLocal`.
- Implemented three cron jobs (generation, e-mail digest, housekeeping) with an e-mail abstraction that
  degrades to a logging sender when no SMTP server is configured, so the app runs anywhere.

### Frontend

- Built a 10-route React 18 SPA (react-router, axios, recharts) with protected routes, lazy-loaded
  pages and an 81 kB-gzipped entry bundle; recharts is isolated in the dashboard chunk.
- Implemented a single-flight token-refresh interceptor: concurrent `401 token_expired` responses share
  one refresh call, which prevents the rotation logic from treating parallel widgets as token reuse.
- Wired the applications table to server-side search, filters, sorting and pagination with debounced
  input, loading/empty/error states and a delete-confirmation dialog.
- Made the dashboard accessible: semantic headings, labelled charts, tabular fallbacks for the funnel
  and distributions, and `prefers-reduced-motion` support.
- Wrote a hand-rolled CSS design system with tokens and three breakpoints; the data table degrades into
  labelled cards on small screens.

### Testing, CI & deployment

- 189 backend tests: 87 unit (policy matrix, JWT, admin guards, scheduler, e-mail rendering) and 102
  MockMvc integration tests through the real filter chain, Flyway-migrated PostgreSQL, Jackson and
  bean validation.
- Integration tests cover invalid input, expired/missing/malformed tokens, role restrictions,
  cross-user access (IDOR), status transitions, analytics maths, reminder dedupe and admin cascades.
- Testcontainers PostgreSQL with an automatic JDBC fallback, so the suite runs with or without a Docker
  daemon; tests roll back and are order-independent.
- 71 frontend tests with Vitest + React Testing Library covering auth flow, form and filter validation,
  pagination wiring and dashboard rendering.
- GitHub Actions pipeline: `mvn verify` (with coverage artifacts), frontend lint → test → build, and a
  build-only check of both container images.
- Multi-stage Dockerfiles (non-root JRE-alpine API, nginx SPA + reverse proxy) and a three-service
  Compose stack with healthchecks and dependency ordering; deployable to any JVM container platform
  (Railway/Render/Fly.io).

---

## How to verify each number

| Claim | Command |
| --- | --- |
| 87 backend unit tests | `cd backend && mvn test` (surefire summary) |
| 102 backend integration tests | `cd backend && mvn verify` (failsafe summary) |
| 83.6 % line / 77.8 % instruction / 63.7 % branch coverage | `cd backend && mvn clean verify`, then `target/site/jacoco-merged/index.html` |
| 71 frontend tests | `cd frontend && npm test` |
| 35 endpoints | `grep -rc "@\(Get\|Post\|Put\|Patch\|Delete\)Mapping" backend/src/main/java/com/jobtrack/web/*Controller.java` |
| 7 tables | `grep -c "CREATE TABLE" backend/src/main/resources/db/migration/V1__init_schema.sql` |
| Bundle sizes | `cd frontend && npm run build` (Vite output table) |
| Security behaviour (404 for IDOR, 401 reasons, 403 for USER on admin routes) | `AuthApiIT`, `ApplicationApiIT`, `AdminApiIT` |
| Analytics maths | `AnalyticsApiIT` (dataset + expected rates/funnel/distribution) |

---

## Interview talking points

Three stories that come straight from the commit history and demonstrate debugging, not just coding:

1. **"A test proved my security feature was a no-op."** Refresh-token reuse detection correctly
   returned `401 token_reused`, but the family revocation was rolled back: `AuthService.refresh` was
   `@Transactional`, so throwing after revoking undid the revocation, and the bulk `@Modifying` update
   left already-loaded entities stale in the persistence context. Fix: make the refresh path
   non-transactional and add `flushAutomatically`/`clearAutomatically` to the bulk queries. Lesson:
   bulk updates and the persistence context do not mix, and security code needs tests that assert the
   *database* state, not just the status code.

2. **"The funnel looked wrong and the code was right."** My first integration test expected
   `applied → assessment → interview → offer` counts of 9/3/3/1, but the API returned 9/4/3/1. The
   funnel is cumulative and derived from the status-history table, so an application that jumped
   `APPLIED → INTERVIEW` still counts at the assessment stage — which is what keeps the funnel
   monotonic. I fixed the test, then documented the rule so the next reader does not have to
   rediscover it.

3. **"Time zones are a data-modelling decision."** A deadline is a day in the user's life, not an
   instant, so it is a `DATE`; an interview is an instant, so it is `TIMESTAMPTZ`. Date-only reminders
   become due at 09:00 in the owner's IANA zone and are stored as UTC. The tests assert the exact
   offset for an `Asia/Kolkata` user (`…T09:00+05:30` vs `…T03:30Z`), because that is the kind of bug
   that only shows up for users in the wrong hemisphere.

Questions to be ready for: why `404` instead of `403`; why rotate refresh tokens at all; how the
single denominator prevents double counting; how reminder generation stays idempotent across replicas;
why entities never appear in a response; what you would change first in production (cookie-based
refresh tokens, rate limiting, ShedLock).

---

## What not to claim

- No production users, traffic, uptime or latency figures — the project has never served real traffic.
- No "improved performance by X %" — there is no before/after benchmark in the repository.
- No "microservices" — it is a modular monolith plus a static SPA, which is the right size for this
  problem and worth defending as a choice rather than dressing up.
- If a résumé line says "e-mail reminders", be precise: e-mail digests are implemented behind an
  interface and disabled by default; in-app reminders are the primary feature.
