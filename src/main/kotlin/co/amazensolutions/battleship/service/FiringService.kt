package co.amazensolutions.battleship.service

import co.amazensolutions.battleship.model.Coordinate
import co.amazensolutions.battleship.model.FireResponse
import co.amazensolutions.battleship.model.FireResult
import co.amazensolutions.battleship.model.GameStatus
import co.amazensolutions.battleship.model.ShotResult
import com.google.inject.Inject
import com.google.inject.Singleton

@Singleton
class FiringService @Inject constructor(
    private val gameService: GameService
) {

    fun fire(gameId: String, playerToken: String, row: Int, col: Int, isServerAiCall: Boolean = false): FireResult {
        val game = gameService.getGame(gameId)
            ?: throw IllegalArgumentException("Game not found: $gameId")
        require(game.status == GameStatus.IN_PROGRESS) {
            "Cannot fire in game with status ${game.status}"
        }

        val playerNumber = game.resolvePlayerNumber(playerToken)
        if (!isServerAiCall) {
            require(!game.isAiPlayer(playerNumber)) {
                "Cannot perform actions for AI player"
            }
        }
        require(playerNumber == game.currentTurn) {
            "It is not your turn"
        }

        val coordinate = Coordinate(row, col)
        val targetBoard = game.opponentState(playerNumber).board

        if (coordinate in targetBoard.shots) {
            return FireResult(
                response = FireResponse(
                    result = ShotResult.ALREADY_SHOT,
                    coordinate = coordinate
                ),
                game = game,
                playerNumber = playerNumber
            )
        }

        val updatedShots = targetBoard.shots + coordinate
        val hitShip = targetBoard.ships.find { ship ->
            coordinate in ship.occupiedCells()
        }

        val updatedHits = if (hitShip != null) targetBoard.hits + coordinate else targetBoard.hits
        val updatedBoard = targetBoard.copy(shots = updatedShots, hits = updatedHits)

        val result = when {
            hitShip == null -> ShotResult.MISS
            hitShip.isSunk(updatedHits) && updatedBoard.allShipsSunk() -> ShotResult.GAME_OVER
            hitShip.isSunk(updatedHits) -> ShotResult.SUNK
            else -> ShotResult.HIT
        }

        val gameOver = result == ShotResult.GAME_OVER
        val nextTurn = if (playerNumber == 1) 2 else 1
        val updatedOpponent = game.opponentState(playerNumber).copy(board = updatedBoard)
        val updatedGame = game.withUpdatedOpponent(playerNumber, updatedOpponent).copy(
            status = if (gameOver) GameStatus.COMPLETED else game.status,
            winner = if (gameOver) playerNumber else null,
            currentTurn = if (!gameOver) nextTurn else game.currentTurn,
            updatedAt = System.currentTimeMillis()
        )
        gameService.saveGame(updatedGame)

        return FireResult(
            response = FireResponse(
                result = result,
                coordinate = coordinate,
                sunkShip = if (result == ShotResult.SUNK || result == ShotResult.GAME_OVER) hitShip?.type else null,
                gameOver = gameOver,
                winnerId = if (gameOver) playerNumber else null
            ),
            game = updatedGame,
            playerNumber = playerNumber
        )
    }
}
