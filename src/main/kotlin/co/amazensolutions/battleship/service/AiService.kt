package co.amazensolutions.battleship.service

import co.amazensolutions.battleship.model.Coordinate
import co.amazensolutions.battleship.model.Game
import co.amazensolutions.battleship.model.GameMode
import co.amazensolutions.battleship.model.GameStatus
import co.amazensolutions.battleship.model.Orientation
import co.amazensolutions.battleship.model.PlacedShip
import co.amazensolutions.battleship.model.ShipType
import com.google.inject.Inject
import com.google.inject.Singleton
import kotlin.random.Random

@Singleton
class AiService @Inject constructor() {

    companion object {
        private val ALL_COORDINATES = (0..9).flatMap { row -> (0..9).map { col -> Coordinate(row, col) } }
    }

    fun placeAiShips(game: Game): Game {
        require(game.mode == GameMode.SINGLE_PLAYER) {
            "AI ships can only be placed in single player games"
        }

        val ships = generateRandomPlacement()
        val aiState = game.player2.copy(
            board = game.player2.board.copy(ships = ships),
            shipsPlaced = true
        )
        return game.copy(player2 = aiState)
    }

    fun chooseAiTarget(game: Game): Coordinate {
        require(game.status == GameStatus.IN_PROGRESS) { "Game is not in progress" }
        require(game.currentTurn == 2) { "It is not AI's turn" }

        val targetBoard = game.player1.board
        val available = ALL_COORDINATES.filter { it !in targetBoard.shots }
        require(available.isNotEmpty()) { "No available coordinates to fire at" }
        return available[Random.nextInt(available.size)]
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
