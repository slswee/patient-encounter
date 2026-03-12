# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
./gradlew build          # compile + test
./gradlew test           # run all tests
./gradlew test --tests "com.sallyli.ApplicationTest.testCreateEncounterSuccess"  # single test
./gradlew run            # start server on :8080
./gradlew buildFatJar    # produce runnable fat JAR
```

## Architecture

This is a Ktor 3.4.0 REST API (Kotlin 2.3.0, Netty engine, kotlinx.serialization) for managing PHI-containing patient encounter records. Storage is in-memory only (no database).

### Request flow

```
HTTP Request
  → Authentication (X-API-Key header, ApiKeyAuth.kt)
  → RequestValidation (CreateEncounterRequest only)
  → Route handler (routes/)
  → EncounterService (business logic + audit writes)
  → Repository (ConcurrentHashMap)
```

`StatusPages` wraps the entire pipeline to catch `RequestValidationException→400`, `NotFoundException→404`, `Throwable→500`. It never surfaces internal detail to clients.

### Dependency wiring

There is no DI framework. `Application.module()` manually constructs `InMemoryEncounterRepository`, `InMemoryAuditRepository`, and `EncounterService`, then passes the service into `configureRouting(service)`. All plugin installation order matters: `configureSerialization` → `configureSecurity` → `configureStatusPages` → `configureValidation` → `configureRouting`.

### Authentication

Custom Ktor `AuthenticationProvider` (`ApiKeyAuthenticationProvider`) reads the `X-API-Key` header and resolves it to an `ApiKeyPrincipal(identity)`. Valid keys are hardcoded in `ApiKeyAuth.kt`. All four API endpoints require this auth; unauthenticated requests receive `401` via the provider's challenge handler (not StatusPages).

### PHI compliance

`PhiRedactingConverter` is a Logback `ClassicConverter` registered as `%phi_msg` in `logback.xml`, replacing `%msg`. It redacts `patientId` values in both JSON (`"patientId": "..."`) and query-param (`patientId=...`) forms before any log output is written. The service layer also avoids logging `patientId` directly.

### Audit trail

Every `CREATE` and `READ` operation (not list) writes an `AuditLog` entry via `InMemoryAuditRepository`. The `accessedBy` field comes from the `ApiKeyPrincipal.identity` of the authenticated caller.

## API

| Method | Path | Description |
|--------|------|-------------|
| POST | `/encounters` | Create encounter → 201 |
| GET | `/encounters/{id}` | Get single encounter → 200 / 404 |
| GET | `/encounters` | List with optional `fromDate`, `toDate`, `providerId`, `patientId` query params |
| GET | `/audit/encounters` | Audit log with optional `fromDate`, `toDate` |

All requests require header `X-API-Key: test-api-key-provider-001` (or `...-002`).

Error shape: `{ "message": "...", "details": ["..."] }`
