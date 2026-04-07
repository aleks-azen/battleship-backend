package co.amazensolutions.battleship.service

import co.amazensolutions.battleship.model.AiMode
import co.amazensolutions.battleship.model.AiState
import co.amazensolutions.battleship.model.Board
import co.amazensolutions.battleship.model.Coordinate
import co.amazensolutions.battleship.model.Game
import co.amazensolutions.battleship.model.GameMode
import co.amazensolutions.battleship.model.GameStatus
import co.amazensolutions.battleship.model.Orientation
import co.amazensolutions.battleship.model.PlacedShip
import co.amazensolutions.battleship.model.ShipType
import co.amazensolutions.battleship.model.ShotResult
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
        val aiPlayerState = game.player2.copy(
            board = game.player2.board.copy(ships = ships),
            shipsPlaced = true
        )
        return game.copy(player2 = aiPlayerState, aiState = AiState())
    }

    fun chooseAiTarget(game: Game): Coordinate {
        require(game.status == GameStatus.IN_PROGRESS) { "Game is not in progress" }
        require(game.currentTurn == 2) { "It is not AI's turn" }

        val targetBoard = game.player1.board
        val available = ALL_COORDINATES.filter { it !in targetBoard.shots }
        require(available.isNotEmpty()) { "No available coordinates to fire at" }

        val state = game.aiState ?: AiState()

        return when (state.mode) {
            AiMode.HUNT -> huntTarget(available)
            AiMode.TARGET -> targetTarget(state, targetBoard) ?: huntTarget(available)
            AiMode.DESTROY -> destroyTarget(state, targetBoard)
                ?: targetTarget(state, targetBoard)
                ?: huntTarget(available)
        }
    }

    fun updateAiStateAfterShot(game: Game, target: Coordinate, result: ShotResult, sunkShipType: ShipType?): Game {
        val currentState = game.aiState ?: AiState()
        val board = game.player1.board

        val newState = when (result) {
            ShotResult.MISS -> currentState
            ShotResult.HIT -> handleHit(currentState, target)
            ShotResult.SUNK, ShotResult.GAME_OVER -> handleSunk(currentState, target, board, sunkShipType)
            ShotResult.ALREADY_SHOT -> currentState
        }

        return game.copy(aiState = newState)
    }

    private fun huntTarget(available: List<Coordinate>): Coordinate {
        val checkerboard = available.filter { (it.row + it.col) % 2 == 0 }
        val candidates = checkerboard.ifEmpty { available }
        return candidates[Random.nextInt(candidates.size)]
    }

    private fun targetTarget(state: AiState, board: Board): Coordinate? {
        for (hit in state.targetHits) {
            val adjacents = adjacentCells(hit).filter { it !in board.shots }
            if (adjacents.isNotEmpty()) {
                return adjacents[Random.nextInt(adjacents.size)]
            }
        }
        return null
    }

    private fun destroyTarget(state: AiState, board: Board): Coordinate? {
        if (state.targetHits.size < 2 || state.destroyDirection == null) return null

        val sorted = state.targetHits.sortedWith(compareBy({ it.row }, { it.col }))
        val first = sorted.first()
        val last = sorted.last()

        val forward = when (state.destroyDirection) {
            Orientation.HORIZONTAL -> if (last.col < 9) Coordinate(last.row, last.col + 1) else null
            Orientation.VERTICAL -> if (last.row < 9) Coordinate(last.row + 1, last.col) else null
        }
        if (forward != null && forward !in board.shots) return forward

        val backward = when (state.destroyDirection) {
            Orientation.HORIZONTAL -> if (first.col > 0) Coordinate(first.row, first.col - 1) else null
            Orientation.VERTICAL -> if (first.row > 0) Coordinate(first.row - 1, first.col) else null
        }
        if (backward != null && backward !in board.shots) return backward

        return null
    }

    private fun handleHit(state: AiState, target: Coordinate): AiState {
        val updatedHits = state.targetHits + target

        return when (state.mode) {
            AiMode.HUNT -> AiState(mode = AiMode.TARGET, targetHits = updatedHits)
            AiMode.TARGET -> {
                val direction = determineDirection(updatedHits)
                if (direction != null) {
                    AiState(mode = AiMode.DESTROY, targetHits = updatedHits, destroyDirection = direction)
                } else {
                    AiState(mode = AiMode.TARGET, targetHits = updatedHits)
                }
            }
            AiMode.DESTROY -> AiState(
                mode = AiMode.DESTROY,
                targetHits = updatedHits,
                destroyDirection = state.destroyDirection
            )
        }
    }

    private fun handleSunk(state: AiState, target: Coordinate, board: Board, sunkShipType: ShipType?): AiState {
        val updatedHits = state.targetHits + target

        val sunkShipCells = if (sunkShipType != null) {
            board.ships.find { it.type == sunkShipType }?.occupiedCells()?.toSet() ?: emptySet()
        } else emptySet()

        val remainingHits = updatedHits.filter { it !in sunkShipCells }

        return when {
            remainingHits.isEmpty() -> AiState(mode = AiMode.HUNT)
            remainingHits.size == 1 -> AiState(mode = AiMode.TARGET, targetHits = remainingHits)
            else -> {
                val direction = determineDirection(remainingHits)
                if (direction != null) {
                    AiState(mode = AiMode.DESTROY, targetHits = remainingHits, destroyDirection = direction)
                } else {
                    AiState(mode = AiMode.TARGET, targetHits = remainingHits)
                }
            }
        }
    }

    private fun determineDirection(hits: List<Coordinate>): Orientation? {
        if (hits.size < 2) return null
        val rows = hits.map { it.row }.toSet()
        val cols = hits.map { it.col }.toSet()
        return when {
            rows.size == 1 -> Orientation.HORIZONTAL
            cols.size == 1 -> Orientation.VERTICAL
            else -> null
        }
    }

    private fun adjacentCells(coord: Coordinate): List<Coordinate> {
        return listOfNotNull(
            if (coord.row > 0) Coordinate(coord.row - 1, coord.col) else null,
            if (coord.row < 9) Coordinate(coord.row + 1, coord.col) else null,
            if (coord.col > 0) Coordinate(coord.row, coord.col - 1) else null,
            if (coord.col < 9) Coordinate(coord.row, coord.col + 1) else null
        )
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
