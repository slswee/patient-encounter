# patient-encouter

This project was created using the [Ktor Project Generator](https://start.ktor.io).

Here are some useful links to get you started:

- [Ktor Documentation](https://ktor.io/docs/home.html)
- [Ktor GitHub page](https://github.com/ktorio/ktor)
- The [Ktor Slack chat](https://app.slack.com/client/T09229ZC6/C0A974TJ9). You'll need to [request an invite](https://surveys.jetbrains.com/s3/kotlin-slack-sign-up) to join.

## Features

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

### Token revocation

### Provider-level authorization 

CallerContext — a small data class carrying identity and role with an isAdmin helper. Passing a single  
object rather than two separate strings keeps the service method signatures clean and makes it easy to
add future attributes (e.g. scopes, organizationId) without changing every call site.

role in JWT — embedded as a custom claim at token mint time in generateToken(identity, role). This means
every request carries its own role — no database lookup needed to answer "is this an admin?". The role
for each identity comes from api.roles config, the same externalized/env-var pattern as api.keys.

Authorization in the service, not the route — the access checks live in EncounterService, not in
EncounterRoutes. This means a future route added by any developer can't accidentally bypass the check by
forgetting to add it. The service is the authoritative enforcement point regardless of how it's called.

Three enforcement points, three behaviors:
- createEncounter — non-admin providing a mismatched providerId gets 403. A provider can't create
  records attributed to someone else.
- getEncounter — non-admin requesting another provider's encounter gets 403. The fact that the encounter
  exists is not revealed (no "it exists but you can't see it" leak).
- listEncounters — non-admin's providerId query param is silently overridden to their own identity.
  Returning 403 here would be confusing UX; silently scoping is the standard pattern (same as how Stripe
  scopes list endpoints to the authenticated account).

ForbiddenException → 403 in StatusPages — keeps the same safe pattern as NotFoundException: the service
throws a typed exception, StatusPages maps it to HTTP, internal detail never leaks to the client.
