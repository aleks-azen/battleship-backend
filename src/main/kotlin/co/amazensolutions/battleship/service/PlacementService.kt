package co.amazensolutions.battleship.service

import co.amazensolutions.battleship.model.Coordinate
import co.amazensolutions.battleship.model.Game
import co.amazensolutions.battleship.model.GameStatus
import co.amazensolutions.battleship.model.PlacedShip
import co.amazensolutions.battleship.model.ShipPlacement
import com.google.inject.Inject
import com.google.inject.Singleton

@Singleton
class PlacementService @Inject constructor(
    private val gameService: GameService
) {

    fun placeShips(gameId: String, placements: List<ShipPlacement>): Game {
        val game = gameService.getGame(gameId)
        require(game.status == GameStatus.PLACING_SHIPS) {
            "Cannot place ships in game with status ${game.status}"
        }

        val placedShips = placements.map { placement ->
            PlacedShip(
                type = placement.type,
                origin = Coordinate(placement.row, placement.col),
                orientation = placement.orientation
            )
        }

        validatePlacements(placedShips)

        val updatedBoard = game.playerBoard.copy(ships = placedShips)
        val updatedGame = game.copy(
            playerBoard = updatedBoard,
            status = if (updatedBoard.allShipsPlaced()) GameStatus.IN_PROGRESS else game.status,
            updatedAt = System.currentTimeMillis()
        )
        gameService.saveGame(updatedGame)
        return updatedGame
    }

    private fun validatePlacements(ships: List<PlacedShip>) {
        val types = ships.map { it.type }
        require(types.distinct().size == types.size) {
            "Duplicate ship types are not allowed"
        }

        val allCells = mutableSetOf<Coordinate>()
        for (ship in ships) {
            val cells = ship.occupiedCells()
            for (cell in cells) {
                require(cell.row in 0..9 && cell.col in 0..9) {
                    "Ship ${ship.type} extends outside the board"
                }
                require(allCells.add(cell)) {
                    "Ships overlap at $cell"
                }
            }
        }
    }
}
