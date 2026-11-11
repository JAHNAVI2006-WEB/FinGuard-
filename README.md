# Money Transfer — Secure Wallet & Payments API

A production-style money movement application built with **Java 17 + Spring Boot 3**:
JWT authentication, role-based access control (USER / ADMIN), atomic balance
transfers with pessimistic locking, a permanent audit trail, input validation,
and automated tests. A minimal web UI is served by the same app.

## Features

- **Auth**: Register / login issuing signed JWTs (HS256). Passwords hashed with BCrypt (cost 12).
- **Roles**: `USER` can use wallets; `ADMIN` can read the audit log. Enforced server-side via Spring Security (`hasRole`, `@EnableMethodSecurity`).
- **Money movement**: `POST /api/wallet/transfer` runs in a single `@Transactional` boundary.
  - Both wallet rows are locked with `PESSIMISTIC_WRITE` **in deterministic order** (prevents deadlocks).
  - Balance is re-checked *after* acquiring the lock — no double-spend, no negative balances, no race conditions.
  - Every transfer writes an immutable `Transaction` row with a UUID reference.
- **Audit trail**: every login, failed login, registration, and transfer is persisted to `audit_logs` (actor, action, details, IP, timestamp) in a `REQUIRES_NEW` transaction so it survives rollbacks.
- **Validation & errors**: Bean Validation on all inputs; centralized `@RestControllerAdvice` returns consistent JSON errors (400/401/403/404/409/422/500) — the app never crashes on bad input.
- **Tests**: unit tests for transfer logic + MockMvc integration tests covering auth, authorization, and error paths.

## Quick start (zero setup — uses in-memory H2)

```bash
mvn spring-boot:run
```

Open http://localhost:8080

Seeded accounts:

| Username | Password   | Role  | Balance   |
|----------|------------|-------|-----------|
| admin    | admin123   | ADMIN | 10,000.00 |
| alice    | demo123    | USER  | 5,000.00  |
| bob      | demo123    | USER  | 2,500.00  |

## API

| Method | Endpoint                  | Auth     | Description              |
|--------|---------------------------|----------|--------------------------|
| POST   | /api/auth/register        | public   | Create account + wallet  |
| POST   | /api/auth/login           | public   | Get JWT                  |
| GET    | /api/wallet/me            | Bearer   | My balance               |
| POST   | /api/wallet/transfer      | Bearer   | `{recipientUsername, amount}` |
| GET    | /api/wallet/transactions  | Bearer   | My transaction history (paged) |
| GET    | /api/admin/audit-logs     | ADMIN    | Audit trail (paged)      |
| GET    | /api/admin/stats          | ADMIN    | App stats                |

Example:

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"demo123"}' | jq -r .token)

curl -X POST localhost:8080/api/wallet/transfer \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"recipientUsername":"bob","amount":100.50}'
```

## Production deployment (Postgres)

Set env vars and activate the `postgres` profile:

```bash
export SPRING_PROFILES_ACTIVE=postgres
export DATABASE_URL=jdbc:postgresql://<host>:5432/moneytransfer
export DATABASE_USERNAME=... DATABASE_PASSWORD=...
export JWT_SECRET=<random-secret-at-least-32-chars>   # REQUIRED
export SEED_ADMIN_PASSWORD=... SEED_DEMO_PASSWORD=...

mvn clean package -DskipTests
java -jar target/money-transfer-1.0.0.jar
```

Deploy the jar anywhere (Render, Railway, Fly.io, a VPS). Set `PORT` if the platform assigns one.

## Running tests

```bash
mvn test
```

## Security notes (for interviews)

- **Why pessimistic locking?** Wallet balance updates are read-modify-write; optimistic `@Version` alone can still lose updates under high contention. `PESSIMISTIC_WRITE` serializes access per wallet row. Locks are taken in sorted user-id order to avoid deadlock cycles.
- **Why JWT + stateless?** No server-side sessions; each request is authenticated by signature. CSRF is disabled because there are no cookies — the token is sent in the `Authorization` header.
- **Why audit in REQUIRES_NEW?** Failed operations roll back business data, but the audit record of the *attempt* must persist.
- **Secrets**: JWT secret and DB credentials come only from env vars; nothing sensitive is committed.
