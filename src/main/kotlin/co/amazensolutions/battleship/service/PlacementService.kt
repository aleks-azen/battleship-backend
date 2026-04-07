package co.amazensolutions.battleship.service

import co.amazensolutions.battleship.model.Coordinate
import co.amazensolutions.battleship.model.Game
import co.amazensolutions.battleship.model.GameStatus
import co.amazensolutions.battleship.model.PlacedShip
import co.amazensolutions.battleship.model.ShipPlacement
import co.amazensolutions.battleship.model.ShipType
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

        val playerNumber = game.resolvePlayerNumber(playerToken)
        require(!game.isAiPlayer(playerNumber)) {
            "Cannot perform actions for AI player"
        }

        val placedShips = placements.map { placement ->
            PlacedShip(
                type = placement.type,
                origin = Coordinate(placement.row, placement.col),
                orientation = placement.orientation
            )
        }

        validatePlacements(placedShips)

        val playerState = game.playerState(playerNumber)
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

        val bothPlaced = updatedGame.player1.shipsPlaced && updatedGame.player2.shipsPlaced
        val finalGame = if (bothPlaced) {
            updatedGame.copy(status = GameStatus.IN_PROGRESS)
        } else {
            updatedGame
        }

        gameService.saveGame(finalGame)
        return finalGame
    }

    private fun validatePlacements(ships: List<PlacedShip>) {
        val requiredTypes = ShipType.entries.toSet()
        val placedTypes = ships.map { it.type }.toSet()
        require(ships.size == requiredTypes.size) {
            "Exactly ${requiredTypes.size} ships required, got ${ships.size}"
        }
        require(placedTypes == requiredTypes) {
            "Must place one of each ship type. Missing: ${requiredTypes - placedTypes}"
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
