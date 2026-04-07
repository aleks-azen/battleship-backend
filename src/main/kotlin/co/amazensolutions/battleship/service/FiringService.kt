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

    fun fire(gameId: String, row: Int, col: Int): FireResponse {
        val game = gameService.getGame(gameId)
        require(game.status == GameStatus.IN_PROGRESS) {
            "Cannot fire in game with status ${game.status}"
        }

        val coordinate = Coordinate(row, col)
        val targetBoard = game.opponentBoard

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
        val updatedGame = game.copy(
            opponentBoard = updatedBoard,
            status = if (gameOver) GameStatus.FINISHED else game.status,
            winnerId = if (gameOver) "player" else null,
            currentTurn = if (!gameOver) "opponent" else game.currentTurn,
            updatedAt = System.currentTimeMillis()
        )
        gameService.saveGame(updatedGame)

        return FireResponse(
            result = result,
            coordinate = coordinate,
            sunkShip = if (result == ShotResult.SUNK || result == ShotResult.GAME_OVER) hitShip?.type else null,
            gameOver = gameOver,
            winnerId = if (gameOver) "player" else null
        )
    }
}
