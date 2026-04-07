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
All responses include CORS headers:
```
Access-Control-Allow-Origin: *
Access-Control-Allow-Methods: GET,POST,PUT,DELETE,OPTIONS
Access-Control-Allow-Headers: Content-Type,X-Player-Token
Content-Type: application/json
```
Use `X-Player-Token` header (NOT `Authorization`). This is a game token, not OAuth.

## CORS

The `OPTIONS` preflight handler MUST return all CORS headers above with 200 status.
Every non-OPTIONS response MUST also include CORS headers.

## Error Handling

- Bad input → `IllegalArgumentException` with descriptive message → 400 response
- Not found → 404 response
- Auth failure (bad player token) → 403 response
- AWS SDK exceptions: catch specific types first, then generic `Exception`
- All errors return JSON `{ "error": "message" }` body

## Multiplayer Model

Game must support 2 human players. Required fields on Game:
- `player1Token: String` — UUID returned at game creation
- `player2Token: String?` — UUID returned at join (null until joined)
- `currentTurn: Int` — 1 or 2 (NOT string literals like "player"/"opponent")
- `player1Board: Board`, `player2Board: Board` — separate boards per player

Turn ownership uses `currentTurn` integer (1 or 2). Map token → player number on every request.
For single-player, player 2 is AI — `player2Token` is `"AI"`.

## Ship Placement Validation

PlacementService MUST validate ALL of these before accepting:
1. Exactly 5 ships (one of each ShipType). NOT partial placements.
2. All cells within 0-9 bounds
3. No overlapping cells between ships
4. No duplicate ship types
Reject with 400 and descriptive error if any check fails.

## Gson Serialization

Use `GsonBuilder().create()`, NOT `Gson()` with no config.
`Set<Coordinate>` in Board — Gson deserializes as List by default. Register TypeAdapters or
use Lists in the JSON representation and convert to Sets on deserialization.
Construct Gson once at class level. NOT per invocation.

## Data Boundary: Game ↔ GameRecord

GameRecord stores the full Game as a JSON blob in `gameData`. Top-level DynamoDB attributes
(`status`, `mode`, `createdAt`, `updatedAt`, `ttl`) are denormalized for queries/GSIs.
When adding a field that needs to be queried or filtered (e.g., `winner`, `mode`), add it
as BOTH a Game field AND a GameRecord top-level attribute. Do NOT rely on scanning the JSON blob.

## Anti-Cheat

- Server is single source of truth. Client is a view layer only.
- NEVER return opponent ship positions in GET /state responses. Filter by player token.
- Validate all mutations server-side (turn order, coordinate bounds, ship placement rules).
- Player tokens are UUIDs — validate via `X-Player-Token` header on every mutating request.
- `GET /games/{id}/state` returns different views depending on which player token is provided.
