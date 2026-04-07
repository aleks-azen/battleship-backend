package co.amazensolutions.battleship.model

enum class GameMode {
    SINGLE_PLAYER,
    MULTIPLAYER
}

enum class GameStatus {
    CREATED,
    PLACING_SHIPS,
    IN_PROGRESS,
    FINISHED
}

enum class Orientation {
    HORIZONTAL,
    VERTICAL
}

enum class ShotResult {
    HIT,
    MISS,
    SUNK,
    ALREADY_SHOT,
    GAME_OVER
}
