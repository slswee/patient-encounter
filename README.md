# patient-encouter

This project was created using the [Ktor Project Generator](https://start.ktor.io).

Here are some useful links to get you started:

- [Ktor Documentation](https://ktor.io/docs/home.html)
- [Ktor GitHub page](https://github.com/ktorio/ktor)

## ktor Features used in this project

Here's a list of features included in this project:

| Name                                                                   | Description                                                                        |
| ------------------------------------------------------------------------|------------------------------------------------------------------------------------ |
| [Content Negotiation](https://start.ktor.io/p/content-negotiation)     | Provides automatic content conversion according to Content-Type and Accept headers |
| [Routing](https://start.ktor.io/p/routing)                             | Provides a structured routing DSL                                                  |
| [kotlinx.serialization](https://start.ktor.io/p/kotlinx-serialization) | Handles JSON serialization using kotlinx.serialization library                     |

## Building & Running

To build or run the project, use one of the following tasks:

| Task                                    | Description                                                          |
| -----------------------------------------|---------------------------------------------------------------------- |
| `./gradlew test`                        | Run the tests                                                        |
| `./gradlew build`                       | Build everything                                                     |
| `./gradlew buildFatJar`                 | Build an executable JAR of the server with all dependencies included |
| `./gradlew buildImage`                  | Build the docker image to use with the fat JAR                       |
| `./gradlew publishImageToLocalRegistry` | Publish the docker image locally                                     |
| `./gradlew run`                         | Run the server                                                       |
| `./gradlew runDocker`                   | Run using the local docker image                                     |

If the server starts successfully, you'll see the following output:

```
2024-12-04 14:32:45.584 [main] INFO  Application - Application started in 0.303 seconds.
2024-12-04 14:32:45.682 [main] INFO  Application - Responding at http://0.0.0.0:8080
```

## API Authentication
- Used a short-lived token (900s expiry time).  a client proves identity with a pre-shared secret, receives a short-lived JWT, and uses that JWT on every subsequent request during the 900s valid timeframe.
- If a token is stolen, it can be added to a denyList and gets revoked. The denyList is stored in an in-memory ConcurrentHashMap.
- Ktor has a built-in JWT provider that handles signature verification and expiry checking automatically.

### How a client calls the API
Step 1 — Get a token

```
POST /oauth/token
Content-Type: application/x-www-form-urlencoded
client_id=provider-001&client_secret=<secret>&grant_type=client_credentials
```

Step 2 — Call any protected endpoint
```
GET /encounters/abc-123
Authorization: Bearer eyJ...
```

Any failure will result in 401 + INVALID_TOKEN audit entry. The response never reveals which check failed.

Step 3 — Revoke the token (optional)

```
POST /oauth/revoke
Authorization: Bearer eyJ...
```
Adds the token's jti to the in-memory `TokenDenylist`. The same token immediately fails step 5 on all future requests.

**Credential configuration (environment variables)**

| Variable   | Format              | Example                               |
|------------|---------------------|---------------------------------------|
| API_KEYS   | secret:identity,... | key1:provider-001,key2:provider-002   |
| API_ROLES  | identity:role,...   | provider-001:provider,admin-001:admin |
| JWT_SECRET | any string          | HMAC-256 signing key                  |

The `API_KEYS` map is keyed by secret (not identity), so there is no way to enumerate valid client IDs from the outside.


### Token revocation
**The Problem**

JWTs are stateless — once issued, the server has no way to invalidate them before they expire. Without revocation, a stolen or leaked token is valid for its full 15-minute TTL.

**The Solution**

Use a server-side `ConcurrentHashMap<jti, expiresAtMs>` stores revoked token IDs. Every token contains a jti (JWT ID) claim — a UUID minted at token creation time. Revoking a token means adding its jti to this map.

If a token is revoked then every subsequent request with the same token will result in a response of 401 Unauthorized

Revoked entries don't accumulate forever. When isRevoked(jti) is called and the stored expiresAtMs has already passed, the entry is removed from the map before returning false. No background thread needed — the map
self-cleans as tokens naturally expire.

In production, we can use Redis to store the denyList (key TTLs, and all instances can access the same list)

## PHI redaction

`patientId` is PHI under HIPAA. 

I extended the Logback converter to make two custom Logback converters, which are registered in `logback.xml`.
`PhiRedactingConverter` intercepts the message in `RequestValidationException`, `NotFoundException`, `ForbiddenException`, and a catch-call `Throwable`.
`PhiRedactingThrowableConverter` intercepts and redacts the exception/stack trace output. 

**What this covers**
- PHI in exception messages throughout the entire codebase — any logger, any class, not just StatusPages
- Ktor's own internal loggers (io.ktor.*, io.netty.*) — they all inherit the root appender with this pattern
- Future developers who write logger.error("msg", exceptionWithPhi) anywhere — automatically protected
- Stack trace frames — already safe (no data in frames), but the redaction passes through harmlessly

**What it doesn't cover**
- `clinicalData` is a free-from JsonObject that can contain anything and it's currently not covered by any redaction pattern.


### Provider-level authorization 

* Only an admin can call GET /encounters on all patient encounters
* A provider can only see their own patients unless explicitly granted broader access.

## What I would do if actually deploying this service in production
- Implement rate limiting on `/oauth/token`
  * There is no rate limiting, lockout, or throttling on the token endpoint. An attacker can brute-force client_secret values at full server speed. Each failed attempt does write an `INVALID_CLIENT` audit entry, but nothing stops the attempts or alerts on volume.
  * In production, add rate limiting at API Gateway on `client_id` or source IP.
- Data storage needs to be in persistent database 
  * Postgres is a great choice, because it offers encryption for patient data and point-in-time recovery for audit logs. 
  * The repository interfaces (EncounterRepository, AuditRepository) are abstracted — we can swap to a PostgreSQl implementation without touching service or route code. 
  * Token denylist is also stored in in-memory ConcurrentHashMap, so it resets on every restart. In production it should be stored in Redis (with key TTLs, so entries automatically expire, no additional cleanup needed)
- TLS enforcement.
  * Currently, there is no TLS configuration, no redirect from HTTP to HTTPS, and no Strict-Transport-Security header on responses. The server runs on plain HTTP. Every token, every JWT, and every piece of PHI in request/response bodies is transmitted in cleartext.
  In production, terminate TLS at a load balancer or reverse proxy (nginx, AWS ALB). The application should also reject non-HTTPS requests or redirect them.
- `patientId` is absent from audit entries. Audit records link to `encounterId` but not `patientId`.
  * Issue: Answering a patient's HIPAA Accounting of Disclosures request ("who accessed my records?") requires joining the audit log against the encounter store. If the encounter store is ever cleared or migrated, those audit entries become unresolvable.
  * I would need to research on the domain subject (HIPAA) and then come up with a tech approach to handle this. For the purpose of the take home assignment I skipped this part.
- Add redaction for `clinicalData`. I'd research how to do redaction using structural pattern. Regex pattern is good enough for `patientId` but is insufficient for `clinicalData`. 

