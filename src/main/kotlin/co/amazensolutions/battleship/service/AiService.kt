package co.amazensolutions.battleship.service

import co.amazensolutions.battleship.model.Board
import co.amazensolutions.battleship.model.Coordinate
import co.amazensolutions.battleship.model.FireResponse
import co.amazensolutions.battleship.model.Game
import co.amazensolutions.battleship.model.GameStatus
import co.amazensolutions.battleship.model.Orientation
import co.amazensolutions.battleship.model.PlacedShip
import co.amazensolutions.battleship.model.PlayerState
import co.amazensolutions.battleship.model.ShipType
import co.amazensolutions.battleship.model.ShotResult
import com.google.inject.Inject
import com.google.inject.Singleton
import kotlin.random.Random

@Singleton
class AiService @Inject constructor(
    private val gameService: GameService
) {

    fun placeAiShips(game: Game): Game {
        require(game.mode == co.amazensolutions.battleship.model.GameMode.SINGLE_PLAYER) {
            "AI ships can only be placed in single player games"
        }

        val ships = generateRandomPlacement()
        val aiState = game.player2.copy(
            board = game.player2.board.copy(ships = ships),
            shipsPlaced = true
        )
        return game.copy(player2 = aiState)
    }

    fun aiTurn(game: Game): Pair<Game, FireResponse> {
        require(game.status == GameStatus.IN_PROGRESS) {
            "Game is not in progress"
        }
        require(game.currentTurn == 2) {
            "It is not AI's turn"
        }

        val targetBoard = game.player1.board
        val allCoordinates = (0..9).flatMap { row -> (0..9).map { col -> Coordinate(row, col) } }
        val available = allCoordinates.filter { it !in targetBoard.shots }

        require(available.isNotEmpty()) { "No available coordinates to fire at" }

        val target = available[Random.nextInt(available.size)]
        val updatedShots = targetBoard.shots + target
        val hitShip = targetBoard.ships.find { ship -> target in ship.occupiedCells() }

        val updatedHits = if (hitShip != null) targetBoard.hits + target else targetBoard.hits
        val updatedBoard = targetBoard.copy(shots = updatedShots, hits = updatedHits)

        val result = when {
            hitShip == null -> ShotResult.MISS
            hitShip.isSunk(updatedHits) && updatedBoard.allShipsSunk() -> ShotResult.GAME_OVER
            hitShip.isSunk(updatedHits) -> ShotResult.SUNK
            else -> ShotResult.HIT
        }

        val gameOver = result == ShotResult.GAME_OVER
        val updatedPlayer1 = game.player1.copy(board = updatedBoard)
        val updatedGame = game.copy(
            player1 = updatedPlayer1,
            status = if (gameOver) GameStatus.COMPLETED else game.status,
            winner = if (gameOver) 2 else null,
            currentTurn = if (!gameOver) 1 else game.currentTurn,
            updatedAt = System.currentTimeMillis()
        )

        val fireResponse = FireResponse(
            result = result,
            coordinate = target,
            sunkShip = if (result == ShotResult.SUNK || result == ShotResult.GAME_OVER) hitShip?.type else null,
            gameOver = gameOver,
            winnerId = if (gameOver) "ai" else null
        )

        return Pair(updatedGame, fireResponse)
    }

    internal fun generateRandomPlacement(): List<PlacedShip> {
        val ships = mutableListOf<PlacedShip>()
        val occupied = mutableSetOf<Coordinate>()

        for (shipType in ShipType.entries) {
            var placed = false
            while (!placed) {
                val orientation = if (Random.nextBoolean()) Orientation.HORIZONTAL else Orientation.VERTICAL
                val maxRow = if (orientation == Orientation.VERTICAL) 10 - shipType.size else 9
                val maxCol = if (orientation == Orientation.HORIZONTAL) 10 - shipType.size else 9
                val row = Random.nextInt(maxRow + 1)
                val col = Random.nextInt(maxCol + 1)

                val ship = PlacedShip(shipType, Coordinate(row, col), orientation)
                val cells = ship.occupiedCells()

                if (cells.none { it in occupied }) {
                    ships.add(ship)
                    occupied.addAll(cells)
                    placed = true
                }
            }
        }
        return ships
    }
}
