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
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable
import software.amazon.awssdk.enhanced.dynamodb.Key
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FiringServiceTest {

    private lateinit var gamesTable: DynamoDbTable<GameRecord>
    private lateinit var gameService: GameService
    private lateinit var firingService: FiringService
    private val gson = GsonBuilder().create()

    @BeforeEach
    fun setup() {
        @Suppress("UNCHECKED_CAST")
        gamesTable = mockk<DynamoDbTable<GameRecord>>(relaxed = true)
        gameService = GameService(gamesTable)
        firingService = FiringService(gameService)
    }

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

    private fun gameWithShips(
        currentTurn: Int = 1,
        player2Ships: List<PlacedShip> = listOf(
            PlacedShip(ShipType.DESTROYER, Coordinate(0, 0), Orientation.HORIZONTAL)
        )
    ): Game {
        return Game(
            gameId = "test-game",
            mode = GameMode.MULTIPLAYER,
            status = GameStatus.IN_PROGRESS,
            player1Token = "p1-token",
            player2Token = "p2-token",
            currentTurn = currentTurn,
            player1 = PlayerState(
                board = Board(
                    ships = listOf(PlacedShip(ShipType.DESTROYER, Coordinate(5, 5), Orientation.HORIZONTAL))
                ),
                shipsPlaced = true
            ),
            player2 = PlayerState(
                board = Board(ships = player2Ships),
                shipsPlaced = true
            )
        )
    }

    @Test
    fun `fire miss returns MISS and switches turn`() {
        val game = gameWithShips()
        stubGame(game)

        val result = firingService.fire("test-game", "p1-token", 5, 5).response

        assertEquals(ShotResult.MISS, result.result)
        assertEquals(Coordinate(5, 5), result.coordinate)
        assertNull(result.sunkShip)
        assertFalse(result.gameOver)
        verify { gamesTable.putItem(any<GameRecord>()) }
    }

    @Test
    fun `fire hit returns HIT`() {
        val game = gameWithShips()
        stubGame(game)

        val result = firingService.fire("test-game", "p1-token", 0, 0).response

        assertEquals(ShotResult.HIT, result.result)
        assertNull(result.sunkShip)
        assertFalse(result.gameOver)
    }

    @Test
    fun `fire sinks ship returns SUNK with ship type`() {
        // Destroyer is size 2 at (0,0) horizontal -> (0,0) and (0,1)
        val game = gameWithShips()
        // First hit (0,0)
        val preHitBoard = game.player2.board.copy(
            shots = setOf(Coordinate(0, 0)),
            hits = setOf(Coordinate(0, 0))
        )
        val preHitGame = game.copy(player2 = game.player2.copy(board = preHitBoard))
        stubGame(preHitGame)

        // Fire at (0,1) to sink the destroyer
        val result = firingService.fire("test-game", "p1-token", 0, 1).response

        assertEquals(ShotResult.GAME_OVER, result.result) // only 1 ship, sinking it ends the game
        assertEquals(ShipType.DESTROYER, result.sunkShip)
        assertTrue(result.gameOver)
    }

    @Test
    fun `fire sinks ship but game continues when other ships remain`() {
        val ships = listOf(
            PlacedShip(ShipType.DESTROYER, Coordinate(0, 0), Orientation.HORIZONTAL),
            PlacedShip(ShipType.CRUISER, Coordinate(2, 0), Orientation.HORIZONTAL)
        )
        val game = gameWithShips(player2Ships = ships)
        // Already hit (0,0)
        val preHitBoard = game.player2.board.copy(
            shots = setOf(Coordinate(0, 0)),
            hits = setOf(Coordinate(0, 0))
        )
        val preHitGame = game.copy(player2 = game.player2.copy(board = preHitBoard))
        stubGame(preHitGame)

        val result = firingService.fire("test-game", "p1-token", 0, 1).response

        assertEquals(ShotResult.SUNK, result.result)
        assertEquals(ShipType.DESTROYER, result.sunkShip)
        assertFalse(result.gameOver)
    }

    @Test
    fun `fire at already shot coordinate returns ALREADY_SHOT`() {
        val game = gameWithShips()
        val withShot = game.copy(
            player2 = game.player2.copy(
                board = game.player2.board.copy(shots = setOf(Coordinate(3, 3)))
            )
        )
        stubGame(withShot)

        val result = firingService.fire("test-game", "p1-token", 3, 3).response

        assertEquals(ShotResult.ALREADY_SHOT, result.result)
    }

    @Test
    fun `fire rejects when not player turn`() {
        val game = gameWithShips(currentTurn = 2)
        stubGame(game)

        val ex = assertThrows<IllegalArgumentException> {
            firingService.fire("test-game", "p1-token", 0, 0)
        }
        assertTrue(ex.message!!.contains("not your turn"))
    }

    @Test
    fun `fire rejects when game not IN_PROGRESS`() {
        val game = Game(
            gameId = "test-game",
            mode = GameMode.MULTIPLAYER,
            status = GameStatus.PLACING_SHIPS,
            player1Token = "p1-token",
            player2Token = "p2-token"
        )
        stubGame(game)

        assertThrows<IllegalArgumentException> {
            firingService.fire("test-game", "p1-token", 0, 0)
        }
    }

    @Test
    fun `fire rejects invalid player token`() {
        val game = gameWithShips()
        stubGame(game)

        assertThrows<IllegalArgumentException> {
            firingService.fire("test-game", "bad-token", 0, 0)
        }
    }

    @Test
    fun `fire rejects out of bounds coordinate`() {
        val game = gameWithShips()
        stubGame(game)

        assertThrows<IllegalArgumentException> {
            firingService.fire("test-game", "p1-token", 10, 0)
        }
    }

    @Test
    fun `fire rejects AI player actions`() {
        val game = gameWithShips().copy(
            mode = GameMode.SINGLE_PLAYER,
            currentTurn = 2
        )
        stubGame(game)

        assertThrows<IllegalArgumentException> {
            firingService.fire("test-game", "p2-token", 0, 0)
        }
    }

    @Test
    fun `fire nonexistent game throws`() {
        every { gamesTable.getItem(any<Key>()) } returns null

        assertThrows<IllegalArgumentException> {
            firingService.fire("nonexistent", "p1-token", 0, 0)
        }
    }

    @Test
    fun `win condition sets game to COMPLETED with winner`() {
        // Single destroyer at (0,0)-(0,1), already hit at (0,0)
        val game = gameWithShips()
        val preHitBoard = game.player2.board.copy(
            shots = setOf(Coordinate(0, 0)),
            hits = setOf(Coordinate(0, 0))
        )
        val preHitGame = game.copy(player2 = game.player2.copy(board = preHitBoard))
        stubGame(preHitGame)

        val result = firingService.fire("test-game", "p1-token", 0, 1).response

        assertTrue(result.gameOver)
        assertEquals(ShotResult.GAME_OVER, result.result)
        assertEquals("player1", result.winnerId)
    }
}
