package co.amazensolutions.battleship.service

import co.amazensolutions.battleship.model.Coordinate
import co.amazensolutions.battleship.model.FireResponse
import co.amazensolutions.battleship.model.GameStatus
import co.amazensolutions.battleship.model.ShotResult
import com.google.inject.Inject
import com.google.inject.Singleton

@Singleton
class FiringService @Inject constructor(
    private val gameService: GameService
) {

    fun fire(gameId: String, playerToken: String, row: Int, col: Int): FireResponse {
        val game = gameService.getGame(gameId)
            ?: throw IllegalArgumentException("Game not found: $gameId")
        require(game.status == GameStatus.IN_PROGRESS) {
            "Cannot fire in game with status ${game.status}"
        }

        val playerNumber = when (playerToken) {
            game.player1Token -> 1
            game.player2Token -> 2
            else -> throw IllegalArgumentException("Invalid player token")
        }
        require(playerNumber == game.currentTurn) {
            "It is not your turn"
        }

        val coordinate = Coordinate(row, col)
        val targetBoard = if (playerNumber == 1) game.player2.board else game.player1.board

        if (coordinate in targetBoard.shots) {
            return FireResponse(
                result = ShotResult.ALREADY_SHOT,
                coordinate = coordinate
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
        val updatedPlayerState = if (playerNumber == 1) {
            game.player2.copy(board = updatedBoard)
        } else {
            game.player1.copy(board = updatedBoard)
        }

        val updatedGame = if (playerNumber == 1) {
            game.copy(
                player2 = updatedPlayerState,
                status = if (gameOver) GameStatus.COMPLETED else game.status,
                winner = if (gameOver) playerNumber else null,
                currentTurn = if (!gameOver) 2 else game.currentTurn,
                updatedAt = System.currentTimeMillis()
            )
        } else {
            game.copy(
                player1 = updatedPlayerState,
                status = if (gameOver) GameStatus.COMPLETED else game.status,
                winner = if (gameOver) playerNumber else null,
                currentTurn = if (!gameOver) 1 else game.currentTurn,
                updatedAt = System.currentTimeMillis()
            )
        }
        gameService.saveGame(updatedGame)

        return FireResponse(
            result = result,
            coordinate = coordinate,
            sunkShip = if (result == ShotResult.SUNK || result == ShotResult.GAME_OVER) hitShip?.type else null,
            gameOver = gameOver,
            winnerId = if (gameOver) "player$playerNumber" else null
        )
    }
}
