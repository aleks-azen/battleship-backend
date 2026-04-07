package co.amazensolutions.battleship.service

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
import co.amazensolutions.battleship.model.persistence.GameRecord
import com.google.gson.GsonBuilder
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiServiceTest {

    private lateinit var gamesTable: DynamoDbTable<GameRecord>
    private lateinit var gameService: GameService
    private lateinit var aiService: AiService

    @BeforeEach
    fun setup() {
        @Suppress("UNCHECKED_CAST")
        gamesTable = mockk<DynamoDbTable<GameRecord>>(relaxed = true)
        gameService = GameService(gamesTable)
        aiService = AiService(gameService)
    }

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

    @Test
    fun `aiTurn fires at unshot coordinate`() {
        val p1Ships = listOf(
            PlacedShip(ShipType.DESTROYER, Coordinate(0, 0), Orientation.HORIZONTAL)
        )
        val game = Game(
            gameId = "test",
            mode = GameMode.SINGLE_PLAYER,
            status = GameStatus.IN_PROGRESS,
            player1Token = "p1",
            player2Token = "p2-ai",
            currentTurn = 2,
            player1 = PlayerState(board = Board(ships = p1Ships), shipsPlaced = true),
            player2 = PlayerState(
                board = Board(
                    ships = listOf(PlacedShip(ShipType.DESTROYER, Coordinate(5, 5), Orientation.HORIZONTAL))
                ),
                shipsPlaced = true
            )
        )

        val (updatedGame, response) = aiService.aiTurn(game)

        assertTrue(response.coordinate.row in 0..9)
        assertTrue(response.coordinate.col in 0..9)
        assertTrue(response.result in listOf(ShotResult.HIT, ShotResult.MISS, ShotResult.SUNK, ShotResult.GAME_OVER))
        // Turn should switch back to player 1 (unless game over)
        if (!response.gameOver) {
            assertEquals(1, updatedGame.currentTurn)
        }
    }

    @Test
    fun `aiTurn rejects when not AI turn`() {
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
            aiService.aiTurn(game)
        }
    }

    @Test
    fun `aiTurn rejects when game not in progress`() {
        val game = Game(
            gameId = "test",
            mode = GameMode.SINGLE_PLAYER,
            status = GameStatus.COMPLETED,
            player1Token = "p1",
            player2Token = "p2-ai",
            currentTurn = 2
        )

        assertThrows<IllegalArgumentException> {
            aiService.aiTurn(game)
        }
    }
}
