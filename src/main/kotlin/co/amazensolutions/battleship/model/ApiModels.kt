package co.amazensolutions.battleship.model

data class CreateGameRequest(
    val mode: GameMode
)

data class CreateGameResponse(
    val gameId: String,
    val playerToken: String,
    val playerNumber: Int,
    val status: GameStatus,
    val mode: GameMode
)

data class JoinGameResponse(
    val playerToken: String,
    val playerNumber: Int
)

data class PlaceShipsRequest(
    val ships: List<ShipPlacement>
)

data class ShipPlacement(
    val type: ShipType,
    val row: Int,
    val col: Int,
    val orientation: Orientation
)

data class FireRequest(
    val row: Int,
    val col: Int
)

data class FireResponse(
    val result: ShotResult,
    val coordinate: Coordinate,
    val sunkShip: ShipType? = null,
    val gameOver: Boolean = false,
    val winnerId: String? = null
)

data class GameStateResponse(
    val gameId: String,
    val status: GameStatus,
    val mode: GameMode,
    val playerBoard: BoardView,
    val opponentBoard: BoardView,
    val currentTurn: String,
    val winnerId: String? = null,
    val updatedAt: Long
)

data class BoardView(
    val ships: List<ShipView>,
    val shots: List<Coordinate>,
    val hits: List<Coordinate>
)

data class ShipView(
    val type: ShipType,
    val origin: Coordinate,
    val orientation: Orientation,
    val sunk: Boolean
)

data class FireResponseWithAi(
    val result: ShotResult,
    val coordinate: Coordinate,
    val sunkShip: ShipType? = null,
    val gameOver: Boolean = false,
    val winnerId: String? = null,
    val aiResult: FireResponse? = null
)

data class FireResult(
    val response: FireResponse,
    val game: Game
)

data class ErrorResponse(
    val message: String,
    val code: String
)
