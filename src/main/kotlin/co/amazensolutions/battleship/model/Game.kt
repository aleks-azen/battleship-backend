package co.amazensolutions.battleship.model

data class Game(
    val gameId: String,
    val mode: GameMode,
    val status: GameStatus,
    val player1: PlayerState = PlayerState(),
    val player2: PlayerState = PlayerState(),
    val currentTurn: Int = 1,
    val player1Token: String,
    val player2Token: String,
    val winner: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
