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
    val aiState: AiState? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun resolvePlayerNumber(token: String): Int = when (token) {
        player1Token -> 1
        player2Token -> 2
        else -> throw IllegalArgumentException("Invalid player token")
    }

    fun playerState(playerNumber: Int): PlayerState =
        if (playerNumber == 1) player1 else player2

    fun opponentState(playerNumber: Int): PlayerState =
        if (playerNumber == 1) player2 else player1

    fun withUpdatedOpponent(playerNumber: Int, state: PlayerState): Game =
        if (playerNumber == 1) copy(player2 = state) else copy(player1 = state)

    fun isAiPlayer(playerNumber: Int): Boolean =
        mode == GameMode.SINGLE_PLAYER && playerNumber == 2

    fun totalMoveCount(): Int =
        player1.board.shots.size + player2.board.shots.size
}
