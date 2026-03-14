# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
./gradlew build          # compile + test
./gradlew test           # run all tests
./gradlew run            # start server on :8080
./gradlew buildFatJar    # produce runnable fat JAR

# Run a single test class
./gradlew test --tests "com.sallyli.routes.EncounterRoutesTest"
# Run a single test method
./gradlew test --tests "com.sallyli.service.EncounterServiceTest.testProviderCannotGetAnotherProvidersEncounter"
```

## Environment Variables

| Variable | Required | Description |
|---|---|---|
| `API_KEYS` | Yes (prod) | Comma-separated `secret:identity` pairs, e.g. `key1:provider-001,key2:provider-002` |
| `API_ROLES` | Yes (prod) | Comma-separated `identity:role` pairs, e.g. `provider-001:provider,admin-001:admin` |
| `JWT_SECRET` | Yes (prod) | HMAC-256 signing secret for JWTs. Defaults to a dev placeholder in `application.yaml` — must be overridden in production. |

Valid roles: `provider`, `admin`. Identities not present in `API_ROLES` default to `provider`.

## Architecture

Ktor 3.4.0 REST API (Kotlin 2.3.0, Netty engine, kotlinx.serialization) for managing PHI-containing patient encounter records. Storage is in-memory only — no database.

### Request flow

```
HTTP Request
  → JWT Authentication    (ApiKeyAuth.kt — validates Bearer token, checks denylist)
  → RequestValidation     (Application.kt — validates CreateEncounterRequest body)
  → Route handler         (routes/)
  → EncounterService      (business logic, RBAC enforcement, audit writes)
  → Repository            (ConcurrentHashMap)
```

`StatusPages` wraps the entire pipeline: `RequestValidationException→400`, `ForbiddenException→403`, `NotFoundException→404`, `Throwable→500`. Internal detail never reaches the client.

### Dependency wiring

No DI framework. `Application.module()` constructs all instances and passes them explicitly. Plugin installation order matters: `configureSerialization → configureSecurity → configureStatusPages → configureValidation → configureRouting`. `auditRepo` is passed to both `configureSecurity` (for auth failure logging) and `EncounterService` (for business operation logging).

### Authentication — OAuth2 Client Credentials (JWT)

Two-step flow:
1. `POST /oauth/token` — exchange `client_id` + `client_secret` for a 15-minute JWT. Role is embedded in the JWT `role` claim at mint time, sourced from `API_ROLES`.
2. All other endpoints — validate `Authorization: Bearer <jwt>` using HMAC-256 signature, expiry check, and denylist check (see `ApiKeyAuth.kt`).

The `jti` claim (UUID) is included in every token to support revocation. `TokenDenylist` stores revoked JTIs with their expiry time and lazily cleans up expired entries.

### Authorization (RBAC)

`CallerContext(identity, role)` is built from the JWT in every route handler and passed to `EncounterService`. Enforcement is in the service layer, not the route layer, so it cannot be bypassed by adding new routes.

| Role | `createEncounter` | `getEncounter` | `listEncounters` |
|---|---|---|---|
| `provider` | Own `providerId` only | Own encounters only | Always scoped to own identity (filter override) |
| `admin` | Any `providerId` | Any encounter | All encounters (filter respected) |

### PHI compliance

PHI redaction is handled by two Logback converters registered in `logback.xml`:

- `PhiRedactingConverter` (`%phi_msg`) — extends `ClassicConverter`, redacts `patientId` in both JSON form (`"patientId": "..."`) and query-param form (`patientId=...`) from the formatted log message.
- `PhiRedactingThrowableConverter` (`%phi_ex`) — extends `ExtendedThrowableProxyConverter`, applies the same redaction logic to the full exception block (message + cause chain + stack trace). Defence-in-depth: no current code path puts `patientId` in an exception message (`SerializationException` for an invalid enum only echoes the bad value and field path, not the full body; service exceptions use `encounterId` or static strings). The converter guards against future code inadvertently including PHI in a thrown exception.

The log pattern in `logback.xml` uses `%phi_msg%n%phi_ex%nopex`: `%phi_ex` outputs the redacted exception block, and `%nopex` suppresses Logback's default (unredacted) exception appending that would otherwise follow.

The `redact()` method on `PhiRedactingConverter` is public so `PhiRedactingThrowableConverter` can delegate to it without duplicating the regex patterns. The service layer also avoids logging `patientId` directly — the converters are a safety net, not the primary control.

### Audit trail

`AuditLog` entries are written for:
- `CREATE` — on `createEncounter`, includes `encounterId`, `accessedBy`, `ipAddress`
- `READ` — on `getEncounter`, same fields
- `LIST` — one entry per encounter returned by `listEncounters` (zero entries if result is empty)
- `REVOKE` — on `POST /oauth/revoke`, includes `accessedBy`, `ipAddress`
- `AUTH_FAILURE` — on any auth failure, includes `reason` (`NO_CREDENTIALS` / `INVALID_TOKEN` / `INVALID_CLIENT`), `accessedBy` (attempted identity where determinable), `ipAddress`

For `INVALID_TOKEN`, the claimed subject is extracted from the unverified JWT for logging — it is not trusted for access control.

### Cache-Control

All PHI-containing responses (`POST /encounters`, `GET /encounters`, `GET /encounters/{id}`, `GET /audit/encounters`) include `Cache-Control: no-store` to prevent browsers and reverse proxies from caching patient data.

## API

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/oauth/token` | None | Exchange client credentials for a 15-min JWT |
| `POST` | `/oauth/revoke` | Bearer JWT | Revoke the current token immediately |
| `POST` | `/encounters` | Bearer JWT | Create encounter → 201. Provider role: `providerId` must match caller. |
| `GET` | `/encounters/{id}` | Bearer JWT | Get encounter → 200 / 403 / 404 |
| `GET` | `/encounters` | Bearer JWT | List encounters. Provider role: always scoped to own records. |
| `GET` | `/audit/encounters` | Bearer JWT | Audit log (all actions including AUTH_FAILURE) |

Query params for `GET /encounters`: `fromDate`, `toDate`, `providerId` (admin only), `patientId`.
Query params for `GET /audit/encounters`: `fromDate`, `toDate`.

Error shape: `{ "message": "...", "details": ["..."] }`

## Test Coverage

95 tests across 10 files. Regenerate with `./gradlew test jacocoTestReport`; HTML report at `build/reports/jacoco/test/html/index.html`.

**Overall: 371/379 lines (98%) · 99/116 methods (85%)**

Per-package breakdown (JaCoCo aggregates all tests together):

| Source package | Lines | Methods | Test file(s) |
|---|---|---|---|
| `routes` | 95/95 (100%) | 15/18 (83%) | `AuthRoutesTest` (15), `EncounterRoutesTest` (18), `AuditRoutesTest` (8) |
| `service` | 60/60 (100%) | 7/7 (100%) | `EncounterServiceTest` (19) |
| `security` | 78/78 (100%) | 19/21 (90%) | `PhiRedactorTest` (5), `PhiRedactingThrowableConverterTest` (4), `PhiRedactionLogIntegrationTest` (4), `TokenDenylistTest` (5) |
| `repository` | 40/41 (98%) | 13/15 (87%) | `EncounterRepositoryTest` (10), `AuditRepositoryTest` (7) |
| `plugins` | 14/17 (82%) | 5/6 (83%) | via route integration tests |
| `model` | 40/40 (100%) | 31/39 (79%) | via all tests |
| `com.sallyli` (root) | 44/48 (92%) | 9/10 (90%) | via route integration tests |

Route tests use `testApplication { setup() }` (full Ktor stack). Unit tests instantiate classes directly — no HTTP overhead, faster failure attribution.

`BaseRouteTest` holds shared `setup()`, `getToken()`, `getAdminToken()`, and `createEncounter()` helpers used by all route test classes.
