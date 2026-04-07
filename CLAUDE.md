# Battleship Backend

Kotlin Lambda (JVM 21) that handles all Battleship game logic via API Gateway proxy events.

## Quick Reference

| What | Command |
|------|---------|
| Build | `./gradlew shadowJar` |
| Test | `./gradlew test` |
| Local run | `GAMES_TABLE=games AWS_REGION=us-east-1 ./gradlew run --args="events/create-game.json"` |
| JAR location | `build/libs/battleship-backend-all.jar` |

## Architecture

- **Handler** — Thin Lambda entrypoint, creates Guice injector, delegates to RequestRouter
- **Router** — Dispatches API Gateway events by HTTP method + path to service methods
- **Services** — GameService (DynamoDB CRUD), PlacementService, FiringService, AiService
- **Config** — AppConfig (env vars), BattleshipModule (Guice DI bindings)
- **Models** — Domain objects, API DTOs, DynamoDB persistence records

## Environment Variables

| Variable | Description |
|----------|-------------|
| `GAMES_TABLE` | DynamoDB table name for game records |

## API Routes

| Method | Path | Description |
|--------|------|-------------|
| POST | /games | Create new game (returns gameId + playerToken) |
| POST | /games/{id}/join | Join multiplayer game (returns playerToken) |
| POST | /games/{id}/ships | Place ships (requires X-Player-Token) |
| POST | /games/{id}/fire | Fire a shot (requires X-Player-Token) |
| GET | /games/{id}/state | Get game state filtered by player token |
| GET | /games/history | List completed games |

## Dependencies

- AWS SDK v2 (DynamoDB Enhanced Client)
- AWS Lambda Java Core + Events
- Google Guice 7.0.0 (DI)
- Gson (JSON serialization)
- MockK + JUnit 5 (testing)
