package co.amazensolutions.battleship.model

enum class GameMode {
    SINGLE_PLAYER,
    MULTIPLAYER
}

enum class GameStatus {
    PLACING_SHIPS,
    IN_PROGRESS,
    COMPLETED
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
