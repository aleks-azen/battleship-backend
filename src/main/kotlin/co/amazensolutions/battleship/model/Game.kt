package co.amazensolutions.battleship.model

data class Game(
    val gameId: String,
    val mode: GameMode,
    val status: GameStatus,
    val playerBoard: Board = Board(),
    val opponentBoard: Board = Board(),
    val currentTurn: String = "player",
    val winnerId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
