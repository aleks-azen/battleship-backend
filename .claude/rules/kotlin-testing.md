---
paths:
  - "src/test/**/*.kt"
---

## Framework

JUnit 5 + MockK. NOT JUnit 4, NOT Mockito.

## Test Structure

Mirror source packages in test directory:
- `service/GameServiceTest.kt` — DynamoDB CRUD tests
- `service/PlacementServiceTest.kt` — Ship placement validation (bounds, overlaps, sizes)
- `service/FiringServiceTest.kt` — Shot processing, sunk detection, win condition
- `service/AiServiceTest.kt` — AI targeting algorithm, mode transitions
- `handler/BattleshipHandlerTest.kt` — Thin wiring test
- `router/RequestRouterTest.kt` — Route dispatch tests
- `model/ShipTest.kt` — occupiedCells(), isSunk() edge cases

Service tests are the priority. Handler/router tests only verify delegation.

## Mocking

Mock at SDK client interface level (`DynamoDbClient`, `DynamoDbEnhancedClient`).
Use `every` / `verify` (sync clients, not suspend).
Use `slot<T>()` captures for request parameter assertions.

For Guice services: use the test constructor to inject mocks directly, bypassing the Guice module.

## Assertions

Use exact expected values. For time-dependent values (TTL), use ±60s tolerance.

## Event Fixtures

Store test event JSON in `events/` directory. Anonymize AWS account IDs (use `123456789012`).
