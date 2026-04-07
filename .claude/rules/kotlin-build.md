---
paths:
  - "*.gradle.kts"
  - "gradle/**"
---

## Shadow JAR

Use `com.gradleup.shadow` plugin (NOT the old `com.github.johnrengelman.shadow`).

Configure exclusions:
- Exclude `LocalRunner` and `LocalContext` classes from production JAR
- Gson is a production dependency (used by RequestRouter for JSON serialization) — do NOT exclude it from the shadow JAR
- Always call `mergeServiceFiles()` for AWS SDK service loader files

```kotlin
tasks.shadowJar {
    archiveBaseName.set("battleship-backend")
    archiveClassifier.set("all")
    archiveVersion.set("")
    mergeServiceFiles()
    exclude("co/amazensolutions/battleship/LocalContext*")
    exclude("co/amazensolutions/battleship/LocalRunnerKt*")
}
```

## Toolchain

Java 21 via `kotlin { jvmToolchain(21) }`. Do NOT set sourceCompatibility/targetCompatibility separately.

## Dependency Versions

Use AWS SDK v2 BOM (`software.amazon.awssdk:bom`) to manage versions consistently.

Keep test dependency versions (MockK, JUnit) consistent.

## LocalRunner

`LocalRunner.kt` with a `main()` that reads JSON event files from `events/`. Wire via `application` plugin.

Run with: `./gradlew run --args="events/create-game.json"`

Required env vars: `GAMES_TABLE`, `AWS_REGION`, `AWS_PROFILE` for local runs.
