---
paths:
  - "src/main/**/*.kt"
---

## Package Structure

Use `config/model/service/handler/router` package layout.

- `handler/` — Thin Lambda entrypoint. Creates Guice injector, delegates to router.
- `router/` — Routes API Gateway proxy events by HTTP method + path to service methods.
- `service/` — All business logic. Receives injected dependencies via Guice `@Inject`.
- `config/` — `AppConfig` data class for env vars, `BattleshipModule` Guice module.
- `model/` — Data classes for domain objects, API request/response DTOs, DynamoDB beans.

## Model File Organization

Group related data classes in one file. Do NOT lump unrelated models into a single `Models.kt`.
Use `model/persistence/` for DynamoDB bean classes.

## Dependency Injection (Guice)

Handler has two constructors:
1. **No-arg** (Lambda runtime) — creates Guice Injector, gets RequestRouter instance
2. **Injected** (tests) — accepts RequestRouter as parameter

```kotlin
class BattleshipHandler : RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private val router: RequestRouter

    constructor() {
        val injector = Guice.createInjector(BattleshipModule())
        this.router = injector.getInstance(RequestRouter::class.java)
    }

    constructor(router: RequestRouter) {
        this.router = router
    }
}
```

Services use `@Inject constructor(...)` for Guice wiring.

## Configuration

Use `AppConfig` with fail-fast `requireNotNull` on every env var.

## AWS SDK

Use **AWS SDK for Java v2** (`software.amazon.awssdk`). NOT the Kotlin SDK.
Clients are `@Singleton` in Guice module — constructed once, reused across warm invocations.

## DynamoDB Models

Use DynamoDB Enhanced Client with `@DynamoDbBean` / `@get:DynamoDbPartitionKey`.
Use `kotlin-noarg` plugin for no-arg constructors on beans.

## API Gateway Proxy Events

Handler receives `APIGatewayProxyRequestEvent`, returns `APIGatewayProxyResponseEvent`.
RequestRouter parses method + path, extracts path params, delegates to service.
All responses include CORS headers.

## Error Handling

- Bad input → `IllegalArgumentException` with descriptive message → 400 response
- Not found → 404 response
- Auth failure (bad player token) → 403 response
- AWS SDK exceptions: catch specific types first, then generic `Exception`
- All errors return JSON `{ "error": "message" }` body

## Anti-Cheat

- Server is single source of truth. Client is a view layer only.
- NEVER return opponent ship positions in GET /state responses.
- Validate all mutations server-side (turn order, coordinate bounds, ship placement rules).
- Player tokens are UUIDs — validate on every mutating request.
