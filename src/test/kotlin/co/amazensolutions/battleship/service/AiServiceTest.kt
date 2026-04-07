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
import co.amazensolutions.battleship.model.PlayerState
import co.amazensolutions.battleship.model.ShipType
import co.amazensolutions.battleship.model.ShotResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiServiceTest {

    private lateinit var aiService: AiService

    @BeforeEach
    fun setup() {
        aiService = AiService()
    }

    // --- Ship Placement Tests (existing) ---

    @Test
    fun `generateRandomPlacement places all 5 ship types`() {
        val ships = aiService.generateRandomPlacement()

        assertEquals(5, ships.size)
        val types = ships.map { it.type }.toSet()
        assertEquals(ShipType.entries.toSet(), types)
    }

    @Test
    fun `generateRandomPlacement all ships within bounds`() {
        repeat(20) {
            val ships = aiService.generateRandomPlacement()
            for (ship in ships) {
                for (cell in ship.occupiedCells()) {
                    assertTrue(cell.row in 0..9, "Row ${cell.row} out of bounds for ${ship.type}")
                    assertTrue(cell.col in 0..9, "Col ${cell.col} out of bounds for ${ship.type}")
                }
            }
        }
    }

    @Test
    fun `generateRandomPlacement no overlapping ships`() {
        repeat(20) {
            val ships = aiService.generateRandomPlacement()
            val allCells = ships.flatMap { it.occupiedCells() }
            assertEquals(allCells.size, allCells.toSet().size, "Ships overlap")
        }
    }

    @Test
    fun `placeAiShips sets player2 ships and marks placed`() {
        val game = Game(
            gameId = "test",
            mode = GameMode.SINGLE_PLAYER,
            status = GameStatus.PLACING_SHIPS,
            player1Token = "p1",
            player2Token = "p2-ai"
        )

        val result = aiService.placeAiShips(game)

        assertTrue(result.player2.shipsPlaced)
        assertEquals(5, result.player2.board.ships.size)
        assertFalse(result.player1.shipsPlaced)
        assertNotNull(result.aiState)
        assertEquals(AiMode.HUNT, result.aiState!!.mode)
    }

    @Test
    fun `placeAiShips rejects multiplayer game`() {
        val game = Game(
            gameId = "test",
            mode = GameMode.MULTIPLAYER,
            status = GameStatus.PLACING_SHIPS,
            player1Token = "p1",
            player2Token = "p2"
        )

        assertThrows<IllegalArgumentException> {
            aiService.placeAiShips(game)
        }
    }

    // --- Hunt Mode Tests ---

    @Test
    fun `chooseAiTarget in hunt mode picks unshot coordinate`() {
        val game = makeInProgressGame()

        val target = aiService.chooseAiTarget(game)

        assertTrue(target.row in 0..9)
        assertTrue(target.col in 0..9)
        assertTrue(target !in game.player1.board.shots)
    }

    @Test
    fun `chooseAiTarget in hunt mode uses checkerboard pattern`() {
        val game = makeInProgressGame()

        repeat(50) {
            val target = aiService.chooseAiTarget(game)
            assertEquals(0, (target.row + target.col) % 2, "Hunt should use checkerboard: ($target)")
        }
    }

    @Test
    fun `chooseAiTarget falls back to non-checkerboard when checkerboard exhausted`() {
        val checkerboardCells = (0..9).flatMap { row ->
            (0..9).map { col -> Coordinate(row, col) }
        }.filter { (it.row + it.col) % 2 == 0 }.toSet()

        val game = makeInProgressGame(shots = checkerboardCells)
        val target = aiService.chooseAiTarget(game)

        assertTrue(target !in checkerboardCells)
        assertTrue((target.row + target.col) % 2 == 1)
    }

    @Test
    fun `chooseAiTarget avoids already-shot coordinates`() {
        val allButOne = (0..9).flatMap { row -> (0..9).map { col -> Coordinate(row, col) } }
            .filter { !(it.row == 5 && it.col == 5) }
            .toSet()

        val game = makeInProgressGame(shots = allButOne)
        val target = aiService.chooseAiTarget(game)
        assertEquals(Coordinate(5, 5), target)
    }

    // --- Target Mode Tests ---

    @Test
    fun `chooseAiTarget in target mode probes adjacent cells`() {
        val hitCoord = Coordinate(5, 5)
        val aiState = AiState(mode = AiMode.TARGET, targetHits = listOf(hitCoord))
        val game = makeInProgressGame(aiState = aiState, shots = setOf(hitCoord))

        val expectedAdjacents = setOf(
            Coordinate(4, 5), Coordinate(6, 5),
            Coordinate(5, 4), Coordinate(5, 6)
        )

        repeat(20) {
            val target = aiService.chooseAiTarget(game)
            assertTrue(target in expectedAdjacents, "Target $target not adjacent to $hitCoord")
        }
    }

    @Test
    fun `chooseAiTarget in target mode skips already shot adjacents`() {
        val hitCoord = Coordinate(5, 5)
        val shotAdjacents = setOf(
            hitCoord,
            Coordinate(4, 5), Coordinate(6, 5), Coordinate(5, 4)
        )
        val aiState = AiState(mode = AiMode.TARGET, targetHits = listOf(hitCoord))
        val game = makeInProgressGame(aiState = aiState, shots = shotAdjacents)

        val target = aiService.chooseAiTarget(game)
        assertEquals(Coordinate(5, 6), target)
    }

    @Test
    fun `chooseAiTarget in target mode at corner only probes valid adjacents`() {
        val hitCoord = Coordinate(0, 0)
        val aiState = AiState(mode = AiMode.TARGET, targetHits = listOf(hitCoord))
        val game = makeInProgressGame(aiState = aiState, shots = setOf(hitCoord))

        val validAdjacents = setOf(Coordinate(1, 0), Coordinate(0, 1))

        repeat(20) {
            val target = aiService.chooseAiTarget(game)
            assertTrue(target in validAdjacents, "Target $target not valid adjacent of corner (0,0)")
        }
    }

    @Test
    fun `chooseAiTarget in target mode falls back to hunt when no adjacents available`() {
        val hitCoord = Coordinate(5, 5)
        val allAdjacents = setOf(
            hitCoord,
            Coordinate(4, 5), Coordinate(6, 5),
            Coordinate(5, 4), Coordinate(5, 6)
        )
        val aiState = AiState(mode = AiMode.TARGET, targetHits = listOf(hitCoord))
        val game = makeInProgressGame(aiState = aiState, shots = allAdjacents)

        val target = aiService.chooseAiTarget(game)
        assertTrue(target !in allAdjacents, "Should fall back to hunt")
    }

    // --- Destroy Mode Tests ---

    @Test
    fun `chooseAiTarget in destroy mode continues horizontal direction`() {
        val hits = listOf(Coordinate(3, 4), Coordinate(3, 5))
        val aiState = AiState(
            mode = AiMode.DESTROY,
            targetHits = hits,
            destroyDirection = Orientation.HORIZONTAL
        )
        val game = makeInProgressGame(aiState = aiState, shots = hits.toSet())

        val target = aiService.chooseAiTarget(game)
        assertTrue(
            target == Coordinate(3, 6) || target == Coordinate(3, 3),
            "Destroy should extend horizontal: got $target"
        )
    }

    @Test
    fun `chooseAiTarget in destroy mode continues vertical direction`() {
        val hits = listOf(Coordinate(3, 5), Coordinate(4, 5))
        val aiState = AiState(
            mode = AiMode.DESTROY,
            targetHits = hits,
            destroyDirection = Orientation.VERTICAL
        )
        val game = makeInProgressGame(aiState = aiState, shots = hits.toSet())

        val target = aiService.chooseAiTarget(game)
        assertTrue(
            target == Coordinate(5, 5) || target == Coordinate(2, 5),
            "Destroy should extend vertical: got $target"
        )
    }

    @Test
    fun `chooseAiTarget in destroy mode reverses when forward blocked`() {
        val hits = listOf(Coordinate(3, 7), Coordinate(3, 8), Coordinate(3, 9))
        val aiState = AiState(
            mode = AiMode.DESTROY,
            targetHits = hits,
            destroyDirection = Orientation.HORIZONTAL
        )
        val game = makeInProgressGame(aiState = aiState, shots = hits.toSet())

        val target = aiService.chooseAiTarget(game)
        assertEquals(Coordinate(3, 6), target, "Should reverse direction when hitting boundary")
    }

    @Test
    fun `chooseAiTarget in destroy mode falls back to target when both directions blocked`() {
        val hits = listOf(Coordinate(3, 4), Coordinate(3, 5))
        val blockedShots = hits.toSet() + Coordinate(3, 3) + Coordinate(3, 6)
        val aiState = AiState(
            mode = AiMode.DESTROY,
            targetHits = hits,
            destroyDirection = Orientation.HORIZONTAL
        )
        val game = makeInProgressGame(aiState = aiState, shots = blockedShots)

        val target = aiService.chooseAiTarget(game)
        // Should fall back to target mode (adjacent of hits) or hunt
        assertTrue(target !in blockedShots, "Should not fire at already-shot cells")
    }

    // --- State Transition Tests ---

    @Test
    fun `updateAiState transitions from HUNT to TARGET on hit`() {
        val game = makeInProgressGame(aiState = AiState(mode = AiMode.HUNT))
        val target = Coordinate(3, 4)

        val updated = aiService.updateAiStateAfterShot(game, target, ShotResult.HIT, null)

        assertEquals(AiMode.TARGET, updated.aiState!!.mode)
        assertEquals(listOf(target), updated.aiState!!.targetHits)
    }

    @Test
    fun `updateAiState stays in HUNT on miss`() {
        val game = makeInProgressGame(aiState = AiState(mode = AiMode.HUNT))
        val target = Coordinate(3, 4)

        val updated = aiService.updateAiStateAfterShot(game, target, ShotResult.MISS, null)

        assertEquals(AiMode.HUNT, updated.aiState!!.mode)
        assertTrue(updated.aiState!!.targetHits.isEmpty())
    }

    @Test
    fun `updateAiState transitions from TARGET to DESTROY on second aligned hit`() {
        val firstHit = Coordinate(3, 4)
        val aiState = AiState(mode = AiMode.TARGET, targetHits = listOf(firstHit))
        val game = makeInProgressGame(aiState = aiState)
        val secondHit = Coordinate(3, 5)

        val updated = aiService.updateAiStateAfterShot(game, secondHit, ShotResult.HIT, null)

        assertEquals(AiMode.DESTROY, updated.aiState!!.mode)
        assertEquals(Orientation.HORIZONTAL, updated.aiState!!.destroyDirection)
        assertEquals(listOf(firstHit, secondHit), updated.aiState!!.targetHits)
    }

    @Test
    fun `updateAiState stays in TARGET on miss`() {
        val firstHit = Coordinate(3, 4)
        val aiState = AiState(mode = AiMode.TARGET, targetHits = listOf(firstHit))
        val game = makeInProgressGame(aiState = aiState)

        val updated = aiService.updateAiStateAfterShot(game, Coordinate(2, 4), ShotResult.MISS, null)

        assertEquals(AiMode.TARGET, updated.aiState!!.mode)
        assertEquals(listOf(firstHit), updated.aiState!!.targetHits)
    }

    @Test
    fun `updateAiState continues DESTROY on hit`() {
        val hits = listOf(Coordinate(3, 4), Coordinate(3, 5))
        val aiState = AiState(mode = AiMode.DESTROY, targetHits = hits, destroyDirection = Orientation.HORIZONTAL)
        val game = makeInProgressGame(aiState = aiState)
        val thirdHit = Coordinate(3, 6)

        val updated = aiService.updateAiStateAfterShot(game, thirdHit, ShotResult.HIT, null)

        assertEquals(AiMode.DESTROY, updated.aiState!!.mode)
        assertEquals(3, updated.aiState!!.targetHits.size)
        assertEquals(Orientation.HORIZONTAL, updated.aiState!!.destroyDirection)
    }

    @Test
    fun `updateAiState transitions from DESTROY to HUNT on sunk with no remaining hits`() {
        val hits = listOf(Coordinate(0, 0))
        val ship = PlacedShip(ShipType.DESTROYER, Coordinate(0, 0), Orientation.HORIZONTAL)
        val board = Board(ships = listOf(ship), shots = setOf(Coordinate(0, 0), Coordinate(0, 1)), hits = setOf(Coordinate(0, 0), Coordinate(0, 1)))
        val aiState = AiState(mode = AiMode.DESTROY, targetHits = hits, destroyDirection = Orientation.HORIZONTAL)
        val game = makeInProgressGame(aiState = aiState, p1Board = board)
        val sunkTarget = Coordinate(0, 1)

        val updated = aiService.updateAiStateAfterShot(game, sunkTarget, ShotResult.SUNK, ShipType.DESTROYER)

        assertEquals(AiMode.HUNT, updated.aiState!!.mode)
        assertTrue(updated.aiState!!.targetHits.isEmpty())
    }

    @Test
    fun `updateAiState transitions to TARGET when sunk ship has remaining hits from other ship`() {
        val destroyerShip = PlacedShip(ShipType.DESTROYER, Coordinate(0, 0), Orientation.HORIZONTAL)
        val cruiserShip = PlacedShip(ShipType.CRUISER, Coordinate(5, 5), Orientation.VERTICAL)
        val board = Board(
            ships = listOf(destroyerShip, cruiserShip),
            shots = setOf(Coordinate(0, 0), Coordinate(0, 1), Coordinate(5, 5)),
            hits = setOf(Coordinate(0, 0), Coordinate(0, 1), Coordinate(5, 5))
        )

        // targetHits includes a hit on the destroyer (being sunk) and a hit on the cruiser
        val aiState = AiState(
            mode = AiMode.DESTROY,
            targetHits = listOf(Coordinate(0, 0), Coordinate(5, 5)),
            destroyDirection = Orientation.HORIZONTAL
        )
        val game = makeInProgressGame(aiState = aiState, p1Board = board)

        val updated = aiService.updateAiStateAfterShot(game, Coordinate(0, 1), ShotResult.SUNK, ShipType.DESTROYER)

        assertEquals(AiMode.TARGET, updated.aiState!!.mode)
        assertEquals(listOf(Coordinate(5, 5)), updated.aiState!!.targetHits)
    }

    @Test
    fun `updateAiState HUNT to HUNT on sunk single-cell scenario`() {
        val ship = PlacedShip(ShipType.DESTROYER, Coordinate(0, 0), Orientation.HORIZONTAL)
        val board = Board(
            ships = listOf(ship),
            shots = setOf(Coordinate(0, 0), Coordinate(0, 1)),
            hits = setOf(Coordinate(0, 0), Coordinate(0, 1))
        )
        val game = makeInProgressGame(aiState = AiState(mode = AiMode.HUNT), p1Board = board)

        val updated = aiService.updateAiStateAfterShot(game, Coordinate(0, 0), ShotResult.SUNK, ShipType.DESTROYER)

        assertEquals(AiMode.HUNT, updated.aiState!!.mode)
    }

    // --- Validation Tests ---

    @Test
    fun `chooseAiTarget rejects when not AI turn`() {
        val game = Game(
            gameId = "test",
            mode = GameMode.SINGLE_PLAYER,
            status = GameStatus.IN_PROGRESS,
            player1Token = "p1",
            player2Token = "p2-ai",
            currentTurn = 1,
            player1 = PlayerState(shipsPlaced = true),
            player2 = PlayerState(shipsPlaced = true)
        )

        assertThrows<IllegalArgumentException> {
            aiService.chooseAiTarget(game)
        }
    }

    @Test
    fun `chooseAiTarget rejects when game not in progress`() {
        val game = Game(
            gameId = "test",
            mode = GameMode.SINGLE_PLAYER,
            status = GameStatus.COMPLETED,
            player1Token = "p1",
            player2Token = "p2-ai",
            currentTurn = 2
        )

        assertThrows<IllegalArgumentException> {
            aiService.chooseAiTarget(game)
        }
    }

    // --- Integration-style Test ---

    @Test
    fun `AI never fires at same cell twice across full game simulation`() {
        val p1Ships = listOf(
            PlacedShip(ShipType.CARRIER, Coordinate(0, 0), Orientation.HORIZONTAL),
            PlacedShip(ShipType.BATTLESHIP, Coordinate(2, 0), Orientation.HORIZONTAL),
            PlacedShip(ShipType.CRUISER, Coordinate(4, 0), Orientation.HORIZONTAL),
            PlacedShip(ShipType.SUBMARINE, Coordinate(6, 0), Orientation.HORIZONTAL),
            PlacedShip(ShipType.DESTROYER, Coordinate(8, 0), Orientation.HORIZONTAL)
        )

        var game = Game(
            gameId = "sim",
            mode = GameMode.SINGLE_PLAYER,
            status = GameStatus.IN_PROGRESS,
            player1Token = "p1",
            player2Token = "AI",
            currentTurn = 2,
            player1 = PlayerState(board = Board(ships = p1Ships), shipsPlaced = true),
            player2 = PlayerState(shipsPlaced = true),
            aiState = AiState()
        )

        val firedAt = mutableSetOf<Coordinate>()
        var shotCount = 0

        while (shotCount < 100) {
            val target = aiService.chooseAiTarget(game)
            assertFalse(target in firedAt, "AI fired at $target twice on shot #$shotCount")
            firedAt.add(target)

            val board = game.player1.board
            val hitShip = board.ships.find { target in it.occupiedCells() }
            val updatedHits = if (hitShip != null) board.hits + target else board.hits
            val updatedBoard = board.copy(shots = board.shots + target, hits = updatedHits)

            val result = when {
                hitShip == null -> ShotResult.MISS
                hitShip.isSunk(updatedHits) && updatedBoard.allShipsSunk() -> ShotResult.GAME_OVER
                hitShip.isSunk(updatedHits) -> ShotResult.SUNK
                else -> ShotResult.HIT
            }
            val sunkType = if (result == ShotResult.SUNK || result == ShotResult.GAME_OVER) hitShip?.type else null

            game = game.copy(
                player1 = game.player1.copy(board = updatedBoard)
            )
            game = aiService.updateAiStateAfterShot(game, target, result, sunkType)

            shotCount++
            if (result == ShotResult.GAME_OVER) break
        }

        assertTrue(game.player1.board.allShipsSunk(), "AI should sink all ships. Took $shotCount shots")
        assertTrue(shotCount <= 100, "AI took too many shots: $shotCount")
    }

    @Test
    fun `AI wins within 70 shots on average across multiple games`() {
        var totalShots = 0
        val numGames = 20

        repeat(numGames) {
            val p1Ships = aiService.generateRandomPlacement()
            var game = Game(
                gameId = "sim-$it",
                mode = GameMode.SINGLE_PLAYER,
                status = GameStatus.IN_PROGRESS,
                player1Token = "p1",
                player2Token = "AI",
                currentTurn = 2,
                player1 = PlayerState(board = Board(ships = p1Ships), shipsPlaced = true),
                player2 = PlayerState(shipsPlaced = true),
                aiState = AiState()
            )

            var shotCount = 0
            while (shotCount < 100) {
                val target = aiService.chooseAiTarget(game)
                val board = game.player1.board
                val hitShip = board.ships.find { target in it.occupiedCells() }
                val updatedHits = if (hitShip != null) board.hits + target else board.hits
                val updatedBoard = board.copy(shots = board.shots + target, hits = updatedHits)

                val result = when {
                    hitShip == null -> ShotResult.MISS
                    hitShip.isSunk(updatedHits) && updatedBoard.allShipsSunk() -> ShotResult.GAME_OVER
                    hitShip.isSunk(updatedHits) -> ShotResult.SUNK
                    else -> ShotResult.HIT
                }
                val sunkType = if (result == ShotResult.SUNK || result == ShotResult.GAME_OVER) hitShip?.type else null

                game = game.copy(player1 = game.player1.copy(board = updatedBoard))
                game = aiService.updateAiStateAfterShot(game, target, result, sunkType)

                shotCount++
                if (result == ShotResult.GAME_OVER) break
            }

            assertTrue(game.player1.board.allShipsSunk(), "AI failed to sink all ships in game $it")
            totalShots += shotCount
        }

        val avgShots = totalShots.toDouble() / numGames
        assertTrue(avgShots <= 70, "AI averaged $avgShots shots, expected <= 70")
    }

    // --- Helpers ---

    private fun makeInProgressGame(
        aiState: AiState? = AiState(),
        shots: Set<Coordinate> = emptySet(),
        p1Board: Board? = null
    ): Game {
        val p1Ships = listOf(
            PlacedShip(ShipType.DESTROYER, Coordinate(0, 0), Orientation.HORIZONTAL)
        )
        val board = p1Board ?: Board(ships = p1Ships, shots = shots)

        return Game(
            gameId = "test",
            mode = GameMode.SINGLE_PLAYER,
            status = GameStatus.IN_PROGRESS,
            player1Token = "p1",
            player2Token = "AI",
            currentTurn = 2,
            player1 = PlayerState(board = board, shipsPlaced = true),
            player2 = PlayerState(shipsPlaced = true),
            aiState = aiState
        )
    }
}
