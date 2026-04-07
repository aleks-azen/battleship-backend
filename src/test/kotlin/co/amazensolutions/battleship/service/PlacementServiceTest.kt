package co.amazensolutions.battleship.service

import co.amazensolutions.battleship.model.Game
import co.amazensolutions.battleship.model.GameMode
import co.amazensolutions.battleship.model.GameStatus
import co.amazensolutions.battleship.model.Orientation
import co.amazensolutions.battleship.model.ShipPlacement
import co.amazensolutions.battleship.model.ShipType
import co.amazensolutions.battleship.model.persistence.GameRecord
import com.google.gson.GsonBuilder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable
import software.amazon.awssdk.enhanced.dynamodb.Key
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlacementServiceTest {

    private lateinit var gamesTable: DynamoDbTable<GameRecord>
    private lateinit var gameService: GameService
    private lateinit var placementService: PlacementService
    private val gson = GsonBuilder().create()

    @BeforeEach
    fun setup() {
        @Suppress("UNCHECKED_CAST")
        gamesTable = mockk<DynamoDbTable<GameRecord>>(relaxed = true)
        gameService = GameService(gamesTable)
        placementService = PlacementService(gameService)
    }

    private fun validPlacements() = listOf(
        ShipPlacement(ShipType.CARRIER, 0, 0, Orientation.HORIZONTAL),
        ShipPlacement(ShipType.BATTLESHIP, 1, 0, Orientation.HORIZONTAL),
        ShipPlacement(ShipType.CRUISER, 2, 0, Orientation.HORIZONTAL),
        ShipPlacement(ShipType.SUBMARINE, 3, 0, Orientation.HORIZONTAL),
        ShipPlacement(ShipType.DESTROYER, 4, 0, Orientation.HORIZONTAL)
    )

    private fun createTestGame(
        mode: GameMode = GameMode.MULTIPLAYER,
        status: GameStatus = GameStatus.PLACING_SHIPS,
        player1Token: String = "p1-token",
        player2Token: String = "p2-token"
    ): Game = Game(
        gameId = "test-game",
        mode = mode,
        status = status,
        player1Token = player1Token,
        player2Token = player2Token
    )

    private fun stubGame(game: Game) {
        val record = GameRecord(
            gameId = game.gameId,
            gameData = gson.toJson(game),
            status = game.status.name,
            mode = game.mode.name,
            createdAt = game.createdAt,
            updatedAt = game.updatedAt
        )
        every { gamesTable.getItem(any<Key>()) } returns record
    }

    @Test
    fun `placeShips valid placement succeeds`() {
        val game = createTestGame()
        stubGame(game)

        val result = placementService.placeShips("test-game", "p1-token", validPlacements())

        assertEquals(GameStatus.PLACING_SHIPS, result.status)
        assertTrue(result.player1.shipsPlaced)
        assertEquals(5, result.player1.board.ships.size)
        verify { gamesTable.putItem(any<GameRecord>()) }
    }

    @Test
    fun `placeShips both players placed transitions to IN_PROGRESS`() {
        val game = createTestGame()
        // Player 1 already placed
        val p1Placed = game.copy(
            player1 = game.player1.copy(
                shipsPlaced = true,
                board = game.player1.board.copy(
                    ships = validPlacements().map {
                        co.amazensolutions.battleship.model.PlacedShip(
                            it.type,
                            co.amazensolutions.battleship.model.Coordinate(it.row, it.col),
                            it.orientation
                        )
                    }
                )
            )
        )
        stubGame(p1Placed)

        val result = placementService.placeShips("test-game", "p2-token", validPlacements())

        assertEquals(GameStatus.IN_PROGRESS, result.status)
        assertTrue(result.player1.shipsPlaced)
        assertTrue(result.player2.shipsPlaced)
    }

    @Test
    fun `placeShips rejects fewer than 5 ships`() {
        val game = createTestGame()
        stubGame(game)

        val partial = validPlacements().take(3)
        val ex = assertThrows<IllegalArgumentException> {
            placementService.placeShips("test-game", "p1-token", partial)
        }
        assertTrue(ex.message!!.contains("Exactly 5 ships required"))
    }

    @Test
    fun `placeShips rejects duplicate ship types`() {
        val game = createTestGame()
        stubGame(game)

        val duplicates = listOf(
            ShipPlacement(ShipType.CARRIER, 0, 0, Orientation.HORIZONTAL),
            ShipPlacement(ShipType.CARRIER, 1, 0, Orientation.HORIZONTAL),
            ShipPlacement(ShipType.CRUISER, 2, 0, Orientation.HORIZONTAL),
            ShipPlacement(ShipType.SUBMARINE, 3, 0, Orientation.HORIZONTAL),
            ShipPlacement(ShipType.DESTROYER, 4, 0, Orientation.HORIZONTAL)
        )
        val ex = assertThrows<IllegalArgumentException> {
            placementService.placeShips("test-game", "p1-token", duplicates)
        }
        assertTrue(ex.message!!.contains("Must place one of each ship type"))
    }

    @Test
    fun `placeShips rejects ships out of bounds`() {
        val game = createTestGame()
        stubGame(game)

        val outOfBounds = listOf(
            ShipPlacement(ShipType.CARRIER, 0, 6, Orientation.HORIZONTAL), // extends to col 10
            ShipPlacement(ShipType.BATTLESHIP, 1, 0, Orientation.HORIZONTAL),
            ShipPlacement(ShipType.CRUISER, 2, 0, Orientation.HORIZONTAL),
            ShipPlacement(ShipType.SUBMARINE, 3, 0, Orientation.HORIZONTAL),
            ShipPlacement(ShipType.DESTROYER, 4, 0, Orientation.HORIZONTAL)
        )
        assertThrows<IllegalArgumentException> {
            placementService.placeShips("test-game", "p1-token", outOfBounds)
        }
    }

    @Test
    fun `placeShips rejects overlapping ships`() {
        val game = createTestGame()
        stubGame(game)

        val overlapping = listOf(
            ShipPlacement(ShipType.CARRIER, 0, 0, Orientation.HORIZONTAL),
            ShipPlacement(ShipType.BATTLESHIP, 0, 0, Orientation.VERTICAL), // overlaps at (0,0)
            ShipPlacement(ShipType.CRUISER, 2, 2, Orientation.HORIZONTAL),
            ShipPlacement(ShipType.SUBMARINE, 3, 0, Orientation.HORIZONTAL),
            ShipPlacement(ShipType.DESTROYER, 4, 0, Orientation.HORIZONTAL)
        )
        val ex = assertThrows<IllegalArgumentException> {
            placementService.placeShips("test-game", "p1-token", overlapping)
        }
        assertTrue(ex.message!!.contains("overlap"))
    }

    @Test
    fun `placeShips rejects when game is not PLACING_SHIPS`() {
        val game = createTestGame(status = GameStatus.IN_PROGRESS)
        stubGame(game)

        assertThrows<IllegalArgumentException> {
            placementService.placeShips("test-game", "p1-token", validPlacements())
        }
    }

    @Test
    fun `placeShips rejects when ships already placed`() {
        val game = createTestGame()
        val withShips = game.copy(
            player1 = game.player1.copy(shipsPlaced = true)
        )
        stubGame(withShips)

        assertThrows<IllegalArgumentException> {
            placementService.placeShips("test-game", "p1-token", validPlacements())
        }
    }

    @Test
    fun `placeShips rejects AI player actions`() {
        val game = createTestGame(mode = GameMode.SINGLE_PLAYER)
        stubGame(game)

        assertThrows<IllegalArgumentException> {
            placementService.placeShips("test-game", "p2-token", validPlacements())
        }
    }

    @Test
    fun `placeShips rejects invalid player token`() {
        val game = createTestGame()
        stubGame(game)

        assertThrows<IllegalArgumentException> {
            placementService.placeShips("test-game", "bad-token", validPlacements())
        }
    }

    @Test
    fun `placeShips rejects nonexistent game`() {
        every { gamesTable.getItem(any<Key>()) } returns null

        assertThrows<IllegalArgumentException> {
            placementService.placeShips("nonexistent", "p1-token", validPlacements())
        }
    }
}
