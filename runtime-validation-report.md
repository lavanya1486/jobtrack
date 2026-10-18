# Runtime Validation Report

**Generated:** 2026-10-18  
**Target:** `D:\jobtrack-source\jobtrack`

## Summary

| Step | Status | Exit code | Details |
| --- | --- | ---: | --- |
| Docker startup | PASS | 0 | PostgreSQL, backend and frontend containers started healthy |
| Backend verification | PASS | 0 | 87 unit tests and 102 integration tests passed in the Docker network |
| Frontend lint | PASS | 0 | ESLint completed with existing Fast Refresh warnings only |
| Frontend tests | PASS | 0 | 6 test files, 71 tests passed |
| Frontend production build | PASS | 0 | Vite production build completed successfully |
| API smoke flow | PASS | 0 | Registration, login and authenticated profile retrieval passed |
| Browser E2E | UNVERIFIED | n/a | Playwright is not included in the repository; browser behavior was smoke-tested through the deployed SPA URL |

## Reproduce locally

```bash
git clone https://github.com/lavanya1486/jobtrack.git
cd jobtrack
cp .env.example .env
docker compose up --build
```

Open `http://localhost:8081`. The API health endpoint is available at
`http://localhost:8080/actuator/health`.

## Validation environment

- Docker Desktop Linux engine: available
- PostgreSQL: Docker container, PostgreSQL 17
- Backend: Maven, Java 21, Spring Boot
- Frontend: Node 22 container, npm, Vite and Vitest
- Database migrations: Flyway on application startup

## Issues found and resolved

1. The README clone command used a placeholder GitHub owner. It now points to
   `https://github.com/lavanya1486/jobtrack.git`.
2. The Compose backend now honors external `DATABASE_URL`, `DATABASE_USER` and
   `DATABASE_PASSWORD` values, while retaining local PostgreSQL defaults.
3. The local integration test database was created inside the Docker network for validation.

## Known limitations

- The public Quick Tunnel deployment depends on the host computer, Docker Desktop and an open
  `cloudflared` process.
- Playwright browser automation was not run; existing frontend tests and live health/API smoke tests
  passed.
- Frontend lint emits existing `react-refresh/only-export-components` warnings but no errors.
