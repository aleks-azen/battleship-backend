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

    fun placeShips(gameId: String, playerToken: String, placements: List<ShipPlacement>): Game {
        val game = gameService.getGame(gameId)
            ?: throw IllegalArgumentException("Game not found: $gameId")
        require(game.status == GameStatus.PLACING_SHIPS) {
            "Cannot place ships in game with status ${game.status}"
        }

        val playerNumber = resolvePlayerNumber(game, playerToken)

        val placedShips = placements.map { placement ->
            PlacedShip(
                type = placement.type,
                origin = Coordinate(placement.row, placement.col),
                orientation = placement.orientation
            )
        }

        validatePlacements(placedShips)

        val playerState = if (playerNumber == 1) game.player1 else game.player2
        require(!playerState.shipsPlaced) {
            "Ships already placed for player $playerNumber"
        }

        val updatedBoard = playerState.board.copy(ships = placedShips)
        val updatedPlayerState = playerState.copy(
            board = updatedBoard,
            shipsPlaced = updatedBoard.allShipsPlaced()
        )

        val updatedGame = if (playerNumber == 1) {
            game.copy(player1 = updatedPlayerState)
        } else {
            game.copy(player2 = updatedPlayerState)
        }

        // Transition to IN_PROGRESS when both players have placed ships
        val bothPlaced = updatedGame.player1.shipsPlaced && updatedGame.player2.shipsPlaced
        val finalGame = if (bothPlaced) {
            updatedGame.copy(status = GameStatus.IN_PROGRESS)
        } else {
            updatedGame
        }

        gameService.saveGame(finalGame)
        return finalGame
    }

    private fun resolvePlayerNumber(game: Game, token: String): Int {
        return when (token) {
            game.player1Token -> 1
            game.player2Token -> 2
            else -> throw IllegalArgumentException("Invalid player token")
        }
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
