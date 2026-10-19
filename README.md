# JobTrack — Job Application Tracker

JobTrack is a small full-stack app for keeping track of applications, interviews, deadlines and
follow-ups. It also has a dashboard with a few useful application metrics.

The backend uses **Spring Boot 3.5, Java 21 and PostgreSQL**. The frontend is a **React/Vite** app.
The project runs locally with Docker Compose. Flyway manages the database schema and the API uses
JWTs for authentication.

---

## Contents

- [Features](#features)
- [Quick start](#quick-start)
- [Running it manually](#running-it-manually)
- [Demo accounts](#demo-accounts)
- [Screenshots](#screenshots)
- [Architecture](#architecture)
- [API reference](#api-reference)
- [Configuration](#configuration)
- [Data model](#data-model)
- [Analytics rules](#analytics-rules)
- [Reminders and scheduled jobs](#reminders-and-scheduled-jobs)
- [Testing](#testing)
- [Deployment](#deployment)
- [Project layout](#project-layout)
- [Known limitations](#known-limitations)

---

## Features

**Authentication & authorisation**
- Register / login / logout with BCrypt-hashed passwords.
- Short-lived JWT access tokens and rotating refresh tokens. Refresh tokens are stored as SHA-256
  hashes; reusing a rotated token revokes its token family.
- `USER` and `ADMIN` roles, checked by Spring Security and again at the service layer.
- Queries are scoped to the signed-in user. A record belonging to another user is returned as `404`.

**Applications**
- Full CRUD: company, title, description, posting URL, employment type, location, work mode, expected
  salary, applied date, deadline, status, notes, tags, next action + next action date.
- Status pipeline `SAVED → APPLIED → ASSESSMENT → INTERVIEW → OFFER / REJECTED / WITHDRAWN` with a
  transition policy enforced server-side (an illegal move is a `400` listing the allowed targets).
- Every status change is written to an append-only `application_status_history` audit trail.
- Server-side search (company + title, case-insensitive), filters (status, employment type, work mode,
  location, tag, applied/deadline date ranges, overdue-only, submitted-only), 9 sort keys with a `-`
  prefix for descending, and pagination that returns real totals.

**Analytics**
- KPI summary: total / submitted / active / closed, responded, reached interview, offers, rejections,
  average days to first response, upcoming interviews, overdue count.
- Response rate, interview rate and offer rate all use submitted applications as their denominator.
- Monthly trend (gap-filled, 1–36 months), status distribution (all seven statuses, shares sum to 1),
  cumulative interview funnel from the status history, and top companies.
- A merged "needs attention" feed: upcoming interviews, deadlines, due next actions and overdue
  reminders, sorted overdue-first.

**Interviews & reminders**
- Interview rounds (phone screen, coding assessment, technical, HR, final, on-site) with date/time,
  duration, meeting link, interviewer, prep notes, feedback and outcome.
- Scheduling a round auto-advances the application to `INTERVIEW`, stamps the first employer response
  and writes an audit entry.
- Auto reminders for deadlines, next actions, interview prep and unanswered applications. Reminder
  timestamps are stored in UTC and rendered in the user's time zone.
- In-app reminders are the default; e-mail digests are an opt-in feature that degrades to a logging
  sender when no SMTP server is configured.

**Administration & profile**
- Admin console: account directory with activity counters, platform statistics, role changes and
  account deletion (with cascade), guarded against self-demotion, self-deletion and removing the last
  administrator.
- Profile: name, IANA time zone (validated — it drives every rendered reminder time), e-mail opt-in and
  password change (which revokes all refresh tokens).

---

## Quick start

Requires **Docker** and the Docker Compose plugin.

```bash
git clone https://github.com/lavanya1486/jobtrack.git
cd jobtrack
cp .env.example .env          # set secrets and local/demo options
docker compose up --build
```

| What | Where |
| --- | --- |
| Web app | http://localhost:8081 |
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| Health | http://localhost:8080/actuator/health |

The compose stack is three services: `db` (PostgreSQL 17), `backend` (Spring Boot, Flyway migrates the
schema on boot) and `frontend` (nginx serving the production build and proxying `/api`, `/actuator` and
`/swagger-ui` to the backend, so the browser only ever talks to one origin). The example environment
file enables demo data for local use; set `APP_SEED_DEMO_DATA=false` before any public deployment.

Stop with `docker compose down`, and add `-v` to also drop the database volume.

---

## Running it manually

Prerequisites: **JDK 21**, **Maven 3.9+**, **Node 20+**, **PostgreSQL 14+** (17 used in development).

### 1. Database

```bash
psql -U postgres -c "CREATE ROLE jobtrack LOGIN PASSWORD 'jobtrack' CREATEDB;"
psql -U postgres -c "CREATE DATABASE jobtrack OWNER jobtrack;"
```

The schema is created by Flyway (`backend/src/main/resources/db/migration`). Hibernate runs with
`ddl-auto: validate`, so it can never silently change your tables.

### 2. API

```bash
cd backend
mvn spring-boot:run
# → http://localhost:8080, Swagger UI at /swagger-ui/index.html
```

Useful overrides: `APP_SEED_DEMO_DATA=true` (demo accounts + sample data), `PORT=9090`,
`DATABASE_URL=jdbc:postgresql://localhost:5432/jobtrack`.

### 3. Web app

```bash
cd frontend
npm install
cp .env.example .env          # optional; defaults to http://localhost:8080
npm run dev
# → http://localhost:5173 (Vite proxies /api to the backend)
```

### 4. Tests

```bash
cd backend && mvn verify       # unit tests + REST integration tests + coverage
cd frontend && npm test && npm run lint && npm run build
```

---

## Demo accounts

Only created when `APP_SEED_DEMO_DATA=true` (the Docker Compose default, **off** in `application.yml`):

| Role | E-mail | Password |
| --- | --- | --- |
| User | `demo@jobtrack.local` | `Demo1234!` |

The demo user gets a deterministic set of applications across every status, interview rounds,
reminders and status history, so the dashboard and analytics have something to show. Change or disable
these credentials before exposing any deployment to the internet.

---

## Screenshots

The screens below are part of the running app. To capture them yourself after
`docker compose up --build`:

1. Sign in as `demo@jobtrack.local` / `Demo1234!`.
2. **Dashboard** (`/dashboard`) — KPI cards, monthly trend, status distribution, funnel, "needs
   attention" feed.
3. **Applications** (`/applications`) — search, filters, sorting, pagination, row actions.
4. **Application detail** (`/applications/{id}`) — edit form, status timeline, interview rounds.
5. **Interviews** (`/interviews`) and **Reminders** (`/reminders`).
6. **Admin** (`/admin`, sign in as the admin account) — directory and platform stats.
7. **Swagger UI** — `http://localhost:8080/swagger-ui/index.html`.

Drop the images into `docs/screenshots/` and reference them here:

```markdown
![Dashboard](docs/screenshots/dashboard.png)
```

---

## Architecture

The code is split into controllers, services, repositories and domain objects. Controllers accept
and return DTOs; JPA entities stay inside the backend.

```
React SPA (Vite, react-router, axios, recharts)
        │  JSON over HTTP
        ▼
Controller (thin)  ──►  Service (rules, transactions)  ──►  Repository (Spring Data JPA)
        │                        │                                   │
   GlobalExceptionHandler   Domain services                    PostgreSQL 17
   (single error shape)     (status policy, analytics,          (Flyway migrations)
                            reminder generation, e-mail)
```

| Layer | Package | Responsibility |
| --- | --- | --- |
| Web | `com.jobtrack.web` | 8 controllers, request binding, status codes, `GlobalExceptionHandler` |
| Security | `com.jobtrack.security` | JWT issuing/parsing, authentication filter, entry point, access-denied handler |
| Service | `com.jobtrack.service` | Business rules, transactions, analytics maths, reminder generation |
| Repository | `com.jobtrack.repository` | 6 Spring Data repositories + specifications and aggregate projections |
| Domain | `com.jobtrack.domain` | 6 entities, 8 enums, the status-transition policy |
| DTO | `com.jobtrack.dto` | Request/response records + mappers, one sub-package per resource |
| Email / Scheduler | `com.jobtrack.email`, `com.jobtrack.scheduler` | Optional SMTP digests, three cron jobs |
| Config | `com.jobtrack.config` | Typed `app.*` properties, OpenAPI, CORS, mail, seeding |

Full detail — request lifecycle, security model, schema rationale, analytics denominators, testing
strategy and the trade-offs behind each decision — is in
[`docs/architecture.md`](docs/architecture.md).

### Error handling

The API uses one JSON shape for errors:

```json
{
  "timestamp": "2026-10-17T12:04:11.482Z",
  "status": 400,
  "error": "bad_request",
  "message": "Cannot move from SAVED to OFFER",
  "path": "/api/applications/12/status",
  "fieldErrors": [{ "field": "company", "message": "Company is required", "rejectedValue": "" }],
  "details": { "from": "SAVED", "to": "OFFER", "allowedTargets": ["APPLIED", "WITHDRAWN"] }
}
```

`error` is a machine-readable code (`bad_request`, `validation_failed`, `unauthorized`, `not_found`,
`conflict`, `invalid_enum_value`, `forbidden`), `fieldErrors` appears only for bean-validation failures
and `details` carries context such as `reason: token_expired`, which the React client uses to decide
between "silently refresh" and "send the user to the login screen".

---

## API reference

All routes are prefixed with `/api`. Everything except `auth/register`, `auth/login`, `auth/refresh`,
the actuator and the OpenAPI docs requires `Authorization: Bearer <accessToken>`.

### Auth

| Method | Path | Description |
| --- | --- | --- |
| POST | `/api/auth/register` | Create an account → `201` with tokens + profile |
| POST | `/api/auth/login` | Sign in → `200` with tokens + profile |
| POST | `/api/auth/refresh` | Exchange a refresh token → new token pair (rotation) |
| POST | `/api/auth/logout` | Revoke a refresh token family → `204` |

### Applications

| Method | Path | Description |
| --- | --- | --- |
| GET | `/api/applications` | Page of applications: `search`, `status[]`, `employmentType[]`, `workMode[]`, `location`, `tag`, `appliedFrom`, `appliedTo`, `deadlineFrom`, `deadlineTo`, `overdueOnly`, `submittedOnly`, `sort`, `page`, `size` |
| POST | `/api/applications` | Create → `201` |
| GET | `/api/applications/{id}` | Application + its interviews + status history |
| PUT | `/api/applications/{id}` | Full update → `200` |
| PATCH | `/api/applications/{id}/status` | Transition status (policy-checked) → `200` |
| DELETE | `/api/applications/{id}` | Delete with cascade → `204` |
| GET | `/api/applications/{id}/status-history` | Audit trail, oldest first |
| GET | `/api/applications/filter-options` | Distinct locations, employment types, work modes and tags for the filter UI |

Sort keys: `NEWEST`, `OLDEST`, `APPLIED_DATE`, `DEADLINE`, `COMPANY`, `TITLE`, `STATUS`, `SALARY`,
`UPDATED`, each optionally prefixed with `-` for descending (e.g. `sort=-DEADLINE`).

### Interviews

| Method | Path | Description |
| --- | --- | --- |
| GET | `/api/applications/{id}/interviews` | Rounds of one application |
| POST | `/api/applications/{id}/interviews` | Schedule a round → `201` (auto-advances the status) |
| GET | `/api/interviews` | `scope=UPCOMING\|PAST\|ALL`, paginated |
| GET | `/api/interviews/upcoming` | Next rounds for the dashboard (`limit`) |
| PUT | `/api/interviews/{id}` | Reschedule / record outcome → `200` |
| DELETE | `/api/interviews/{id}` | Cancel a round → `204` |

### Reminders

| Method | Path | Description |
| --- | --- | --- |
| GET | `/api/reminders` | `scope=OPEN\|OVERDUE\|UPCOMING\|COMPLETED\|ALL`, paginated |
| GET | `/api/reminders/upcoming` | Due within `days`, at most `limit` |
| POST | `/api/reminders` | Manual reminder → `201` |
| PUT | `/api/reminders/{id}` | Update → `200` |
| PATCH | `/api/reminders/{id}/complete` | Complete (idempotent) → `200` |
| PATCH | `/api/reminders/{id}/reopen` | Undo completion → `200` |
| DELETE | `/api/reminders/{id}` | Delete → `204` |

### Analytics

| Method | Path | Description |
| --- | --- | --- |
| GET | `/api/analytics/summary` | KPIs, rates, status distribution, funnel, top companies |
| GET | `/api/analytics/trends` | Monthly series, `months` 1–36 |
| GET | `/api/analytics/upcoming` | Merged feed, `days` 1–90 and `limit` 1–25 |

### Profile & admin

| Method | Path | Description |
| --- | --- | --- |
| GET | `/api/users/me` | Current profile (also the cheapest token check) |
| PATCH | `/api/users/me` | Name, IANA time zone, e-mail opt-in |
| POST | `/api/users/me/password` | Change password, revokes all sessions → `204` |
| GET | `/api/admin/users` | **ADMIN** — accounts with activity counters |
| GET | `/api/admin/stats` | **ADMIN** — platform statistics |
| PATCH | `/api/admin/users/{id}/role` | **ADMIN** — promote/demote (revokes their tokens) |
| DELETE | `/api/admin/users/{id}` | **ADMIN** — delete account + all data → `204` |

### Swagger UI

`/swagger-ui/index.html` documents every endpoint with request/response schemas. Click **Authorize**
and paste a token *without* the `Bearer ` prefix (the scheme is declared on the security requirement),
obtained from `POST /api/auth/login`.

---

## Configuration

Everything is environment-driven; there is no secret with a usable default.

| Variable | Default | Purpose |
| --- | --- | --- |
| `PORT` | `8080` | HTTP port |
| `SPRING_PROFILES_ACTIVE` | — | `prod` requires `APP_JWT_SECRET` |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/jobtrack` | JDBC URL |
| `DATABASE_USER` / `DATABASE_PASSWORD` | `jobtrack` / `jobtrack` | DB credentials |
| `DB_POOL_SIZE` | `10` | HikariCP pool size |
| `APP_JWT_SECRET` | *(blank → random dev key)* | HMAC-SHA256 key, ≥ 32 chars. A blank value generates an ephemeral key and logs a warning; with the `prod` profile the app refuses to start |
| `APP_JWT_ACCESS_TTL` | `30m` | Access token lifetime |
| `APP_JWT_REFRESH_TTL` | `7d` | Refresh token lifetime |
| `APP_JWT_ISSUER` | `jobtrack-api` | `iss`/`aud` claim, verified on parse |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` | Comma-separated exact origins |
| `APP_REMINDERS_ENABLED` | `true` | Run the scheduler at all |
| `APP_REMINDERS_CRON` | `0 */15 * * * *` | Reminder generation |
| `APP_REMINDERS_DEADLINE_LEAD` | `48h` | How early a deadline reminder appears |
| `APP_REMINDERS_INTERVIEW_LEAD` | `24h` | How early interview prep appears |
| `APP_REMINDERS_FOLLOW_UP_AFTER` | `7d` | Unanswered-application follow-up |
| `APP_REMINDERS_LOOK_AHEAD_DAYS` | `14` | Window scanned per run |
| `APP_REMINDERS_EMAIL_ENABLED` | `false` | Send e-mail digests |
| `APP_REMINDERS_EMAIL_CRON` | `0 0 8 * * *` | Digest schedule |
| `APP_REMINDERS_EMAIL_LEAD` | `24h` | How early the digest goes out |
| `APP_REMINDERS_HOUSEKEEPING_CRON` | `0 30 3 * * *` | Purge expired tokens/reminders |
| `APP_SEED_DEMO_DATA` | `false` | Seed demo accounts and sample data |
| `SPRING_MAIL_HOST` … | — | Standard Spring Mail properties enable the SMTP sender |
| `LOG_LEVEL_APP` / `LOG_LEVEL_SQL` | `INFO` / `WARN` | Logging |

Frontend (`frontend/.env`, see `frontend/.env.example`):

| Variable | Default | Purpose |
| --- | --- | --- |
| `VITE_API_BASE_URL` | `/api` | API base URL; leave relative when a proxy (Vite dev server or nginx) forwards `/api` |
| `VITE_BACKEND_URL` | `http://localhost:8080` | Where the Vite dev-server proxy forwards to |

---

## Data model

```
users 1 ──── * job_applications 1 ──── * interviews
                      │ 1
                      ├──── * application_tags              (free-form labels)
                      ├──── * application_status_history   (append-only audit trail)
                      └──── * reminders                    (auto + manual)
users 1 ──── * refresh_tokens                              (hash, family id, revoked_at)
```

| Table | Notes |
| --- | --- |
| `users` | Unique e-mail (stored lower-case), BCrypt hash, role, `enabled`, IANA `timezone`, e-mail opt-in |
| `job_applications` | All tracking fields, `first_response_at` (set once, drives the response rate), CHECK constraints for every enum, `ON DELETE CASCADE` from `users` |
| `interviews` | Round type, `scheduled_at TIMESTAMPTZ`, duration, link, interviewer, prep notes, feedback, outcome |
| `application_tags` | `(application_id, tag)` composite key, `ON DELETE CASCADE`, functional index on `lower(tag)` |
| `application_status_history` | `from_status`, `to_status`, `changed_by` (SET NULL on user deletion), `reason`, timestamp |
| `reminders` | Type, source (`AUTO`/`MANUAL`), `dedupe_key`, `due_at TIMESTAMPTZ`, completed/notified/e-mail timestamps |
| `refresh_tokens` | SHA-256 `token_hash` (unique), `family_id`, `revoked_at`, `replaced_by_token_hash` |

Timestamps are `TIMESTAMPTZ` (instants) or `DATE` (calendar values such as deadlines — a deadline is a
day, not a moment). Indexes: functional indexes on `lower(company)` / `lower(job_title)` for search,
composite indexes on `(user_id, status)`, `(user_id, applied_at)`, `(user_id, deadline)` and
`(user_id, next_action_date)` for the list queries, and a **partial unique index**
`ux_reminders_open_dedupe (user_id, dedupe_key) WHERE completed_at IS NULL` that makes reminder
generation idempotent even if two scheduler runs overlap.

Schema: [`backend/src/main/resources/db/migration/V1__init_schema.sql`](backend/src/main/resources/db/migration/V1__init_schema.sql).

---

## Analytics rules

The point of the analytics page is that the numbers are defensible, so the rules are explicit and
covered by tests:

- **Denominator** for response rate, interview rate and offer rate = *submitted* applications
  (`status <> SAVED`). Drafts are excluded from all three, so the rates are comparable and can never
  exceed 100%.
- **Responded** = `first_response_at IS NOT NULL`, stamped once when the employer moves the application
  to `ASSESSMENT`, `INTERVIEW`, `OFFER` or `REJECTED`. Re-opening a rejected application does not reset
  it, so a candidate is never counted twice.
- **Reached interview** = status is `INTERVIEW` or `OFFER`, **or** at least one interview round exists.
  An application with three rounds still counts once.
- Rates are rounded to 4 decimals and are `0` (never `NaN` or `null`) when there is no data; fields
  with no meaningful value (e.g. average days to first response with zero responses) are omitted from
  the JSON rather than sent as `null`.
- **Status distribution** always emits all seven statuses including zeros, with shares of the *total*
  (they sum to 1.0), so the chart does not jump when a status empties out.
- **Funnel** is cumulative and derived from `application_status_history.to_status`: applied →
  assessment → interview → offer. Because it reads history, an application that jumped straight from
  `APPLIED` to `INTERVIEW` still counts at the assessment stage, and the funnel is monotonic by
  construction.
- **Trend** is gap-filled per month (months with no activity show 0, not a hole) and counts only
  submitted applications.

---

## Reminders and scheduled jobs

Three independent cron jobs (`com.jobtrack.scheduler.ReminderScheduler`), so a slow e-mail run cannot
delay reminder generation:

1. **Generation** (every 15 min) — creates deadline, next-action, interview-prep and follow-up
   reminders. Idempotent through dedupe keys plus the partial unique index, so overlapping runs or
   several instances are safe. Reminders are regenerated in place when a deadline moves, and dropped
   when the application is closed or deleted.
2. **E-mail digests** (daily) — only for users who opted in, only when SMTP is configured, and only
   once per reminder (`email_sent_at`). Failures are logged, never rethrown.
3. **Housekeeping** (nightly) — purges expired refresh tokens and reminders completed more than 180
   days ago.

Time handling: everything is stored as a UTC instant; a date-only reminder (a deadline, a next action)
becomes due at **09:00 in the owner's IANA time zone**, and every response carries both `dueAt` (UTC)
and `dueAtLocal` (rendered in the user's zone) so the client never has to guess.

---

## Testing

| Suite | Tests | What it covers |
| --- | --- | --- |
| Backend unit (`mvn test`) | **87** | Status-transition policy (37), sort/parse of query parameters (22), JWT issuing & validation (9), admin guards (7), reminder scheduler (7), e-mail digest rendering (5) |
| Backend integration (`mvn verify`) | **102** | `AuthApiIT` (20), `ApplicationApiIT` (25), `AnalyticsApiIT` (16), `InterviewReminderApiIT` (22), `AdminApiIT` (19) — full MockMvc requests against a real PostgreSQL |
| Frontend (`npm test`) | **71** | Vitest + React Testing Library: auth flow, login form validation, application form validation, filter/search/pagination wiring, dashboard rendering, date & number formatting |

Combined JaCoCo coverage (unit + integration): **83.6 % of lines, 77.8 % of instructions,
63.7 % of branches**; services 90.5 %, domain 95.2 %, security 89.6 %. Reports land in
`backend/target/site/jacoco-merged/index.html` after `mvn verify`.

What the integration tests explicitly assert:

- invalid input → `400 validation_failed` with `fieldErrors`, invalid enum values → `400`;
- missing, malformed and expired tokens → `401` with a machine-readable `details.reason`;
- a `USER` calling an admin route → `403`;
- **cross-user access (IDOR)** → `404` on read, update, delete, status change, interviews and reminders;
- refresh-token rotation, reuse detection (family revocation) and logout;
- status-transition policy, including that drafts produce no reminders;
- analytics maths against a hand-built dataset (rates, distribution shares, cumulative funnel, trends);
- reminder generation idempotency, timezone rendering, completion/reopen and sync with the application;
- admin cascade deletion, role hand-over and the guards against self-service.

The integration tests start PostgreSQL through **Testcontainers**. If no Docker daemon is reachable
they fall back to `TEST_DB_URL` (or `jdbc:postgresql://localhost:5432/jobtrack_test`), so they also run
on a machine with only a local PostgreSQL:

```bash
cd backend
mvn verify                                            # unit + integration + coverage
mvn test-compile failsafe:integration-test -Dit.test=ApplicationApiIT   # one class
cd frontend
npm test                                              # vitest run
npm run test:coverage                                 # with @vitest/coverage-v8
npm run lint                                          # eslint (flat config)
```

---

## Deployment

The repository includes a [`render.yaml`](render.yaml) for deploying the API and frontend to Render.
It creates a Docker web service for the backend and a static site for the Vite build.

To use it:

1. Create a PostgreSQL database (Neon works, but any PostgreSQL provider is fine).
2. Create a new Render Blueprint from this repository.
3. Set `DATABASE_URL`, `DATABASE_USER` and `DATABASE_PASSWORD` on the API service.
4. Check the generated service URLs and update `APP_CORS_ALLOWED_ORIGINS` if Render assigns
   different names.

`APP_JWT_SECRET` is generated by the Blueprint. Keep `APP_SEED_DEMO_DATA=false` on a public
deployment. The Render setup is optional; Docker Compose is still the simplest way to run the
project locally.

---

## Project layout

```
jobtrack/
├── backend/
│   ├── pom.xml                         Spring Boot 3.5.16, Java 21, failsafe + JaCoCo
│   ├── Dockerfile
│   └── src/
│       ├── main/java/com/jobtrack/
│       │   ├── config/                 typed app.* properties, OpenAPI, CORS, mail, seeding
│       │   ├── domain/                 6 entities, 8 enums, status-transition policy
│       │   ├── dto/                    request/response records + mappers (no entities leak out)
│       │   ├── email/                  digest sender interface, SMTP + logging implementations
│       │   ├── exception/              ApiException hierarchy
│       │   ├── repository/             6 Spring Data repositories + query support
│       │   ├── scheduler/              three cron jobs
│       │   ├── security/               JWT service, filter, entry point, user details
│       │   ├── service/                business logic and transactions
│       │   └── web/                    8 controllers + global error handling
│       ├── main/resources/
│       │   ├── application.yml         env-var driven, ddl-auto: validate
│       │   ├── application-prod.yml    requires a real JWT secret
│       │   └── db/migration/           Flyway V1__init_schema.sql
│       └── test/java/com/jobtrack/     unit tests + 5 REST integration suites + Testcontainers support
├── frontend/
│   ├── Dockerfile, nginx.conf          static SPA + reverse proxy to the API
│   ├── vite.config.js                  dev proxy, vitest (jsdom) config
│   ├── eslint.config.js
│   └── src/
│       ├── api/                        axios client (token refresh, single-flight), endpoint map
│       ├── auth/                       token storage, auth context, protected routes
│       ├── components/                 table, filters, forms, modal, toasts, charts
│       ├── hooks/, utils/              debounce, constants (statuses, transitions), formatting
│       ├── pages/                      10 lazy-loaded routes
│       └── tests/                      71 Vitest + Testing Library tests
├── docs/
│   ├── architecture.md                 design decisions, schema, security model
│   └── resume-bullets.md               ready-to-use résumé phrasing
├── .github/workflows/ci.yml            backend (mvn verify), frontend (lint + test + build), images
├── docker-compose.yml
├── .env.example
└── README.md
```

---

## Known limitations

Deliberate scope decisions, listed so nothing is over-claimed:

- **No rate limiting** on the auth endpoints. BCrypt (strength 10) plus token-family revocation limit
  the damage, but a production deployment should sit behind a gateway or add bucket4j/Redis throttling.
- **No distributed scheduler lock.** Reminder generation is idempotent at the database level (partial
  unique index), so extra replicas do duplicate work but cannot duplicate reminders. ShedLock would be
  the next step.
- **E-mail is optional and digest-only.** Without SMTP credentials the app logs what it would have
  sent; there are no per-reminder transactional e-mails.
- **E-mail addresses are immutable** through the profile API by design (changing one should require
  verification); an admin can delete the account instead.
- **No file/attachment storage** (résumés, offer letters) — out of scope.
- Charts are rendered with recharts and carry accessible labels/titles, but the dashboard has not been
  audited with a screen reader.

---

## License

MIT — see [`LICENSE`](LICENSE).
