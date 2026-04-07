package co.amazensolutions.battleship.service

import co.amazensolutions.battleship.model.Coordinate
import co.amazensolutions.battleship.model.GameMode
import co.amazensolutions.battleship.model.GameStatus
import co.amazensolutions.battleship.model.Orientation
import co.amazensolutions.battleship.model.ShipPlacement
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
import software.amazon.awssdk.enhanced.dynamodb.Key
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MultiplayerFlowTest {

    private lateinit var gamesTable: DynamoDbTable<GameRecord>
    private lateinit var gameService: GameService
    private lateinit var placementService: PlacementService
    private lateinit var firingService: FiringService
    private val gson = GsonBuilder().create()

    // In-memory store — single game per test
    private var latestRecord: GameRecord? = null

    @BeforeEach
    fun setup() {
        latestRecord = null

        @Suppress("UNCHECKED_CAST")
        gamesTable = mockk<DynamoDbTable<GameRecord>>(relaxed = true)

        // Wire putItem to store the latest record
        every { gamesTable.putItem(any<GameRecord>()) } answers {
            latestRecord = firstArg()
        }

        // Wire getItem to return the latest record
        every { gamesTable.getItem(any<Key>()) } answers {
            latestRecord
        }

        gameService = GameService(gamesTable)
        placementService = PlacementService(gameService)
        firingService = FiringService(gameService)
    }

    private fun standardPlacements(startRow: Int = 0) = listOf(
        ShipPlacement(ShipType.CARRIER, startRow, 0, Orientation.HORIZONTAL),
        ShipPlacement(ShipType.BATTLESHIP, startRow + 1, 0, Orientation.HORIZONTAL),
        ShipPlacement(ShipType.CRUISER, startRow + 2, 0, Orientation.HORIZONTAL),
        ShipPlacement(ShipType.SUBMARINE, startRow + 3, 0, Orientation.HORIZONTAL),
        ShipPlacement(ShipType.DESTROYER, startRow + 4, 0, Orientation.HORIZONTAL)
    )

    @Test
    fun `full multiplayer game flow - create, join, place, fire, win`() {
        // 1. Create multiplayer game
        val game = gameService.createGame(GameMode.MULTIPLAYER)
        val gameId = game.gameId
        val p1Token = game.player1Token
        val p2Token = game.player2Token

        assertEquals(GameStatus.PLACING_SHIPS, game.status)
        assertEquals(GameMode.MULTIPLAYER, game.mode)

        // 2. Join game (player2 token already assigned at creation)
        val joinedGame = gameService.getGame(gameId)
        assertNotNull(joinedGame)
        assertNotNull(joinedGame.player2Token)

        // 3. Player 1 places ships (row 0-4, all horizontal)
        val afterP1Place = placementService.placeShips(gameId, p1Token, standardPlacements(0))
        assertTrue(afterP1Place.player1.shipsPlaced)
        assertFalse(afterP1Place.player2.shipsPlaced)
        assertEquals(GameStatus.PLACING_SHIPS, afterP1Place.status)

        // 4. Player 2 places ships (row 5-9, all horizontal) — triggers IN_PROGRESS
        val afterP2Place = placementService.placeShips(gameId, p2Token, standardPlacements(5))
        assertTrue(afterP2Place.player1.shipsPlaced)
        assertTrue(afterP2Place.player2.shipsPlaced)
        assertEquals(GameStatus.IN_PROGRESS, afterP2Place.status)

        // 5. Alternate firing — player 1 systematically sinks all of player 2's ships
        // Player 2's ships at rows 5-9: CARRIER(5), BATTLESHIP(6), CRUISER(7), SUBMARINE(8), DESTROYER(9)
        // Player 1's ships at rows 0-4: same layout
        // Total p2 ship cells = 5+4+3+3+2 = 17

        val p2ShipCells = listOf(
            Coordinate(5, 0), Coordinate(5, 1), Coordinate(5, 2), Coordinate(5, 3), Coordinate(5, 4),
            Coordinate(6, 0), Coordinate(6, 1), Coordinate(6, 2), Coordinate(6, 3),
            Coordinate(7, 0), Coordinate(7, 1), Coordinate(7, 2),
            Coordinate(8, 0), Coordinate(8, 1), Coordinate(8, 2),
            Coordinate(9, 0), Coordinate(9, 1)
        )

        // Player 2 fires misses at cols 5-9, rows 5-9 (safe — p1 ships are rows 0-4, cols 0-4)
        val p2MissTargets = (5..9).flatMap { r -> (5..9).map { c -> Coordinate(r, c) } }.iterator()

        for ((i, target) in p2ShipCells.withIndex()) {
            val isLastShot = i == p2ShipCells.size - 1

            // Ensure it's player 1's turn; if not, have player 2 fire a miss first
            val currentGame = gameService.getGame(gameId)!!
            if (currentGame.currentTurn == 2) {
                val miss = p2MissTargets.next()
                firingService.fire(gameId, p2Token, miss.row, miss.col)
            }

            // Now fire player 1's shot
            val result = firingService.fire(gameId, p1Token, target.row, target.col)
            if (isLastShot) {
                assertTrue(result.response.gameOver)
                assertEquals(ShotResult.GAME_OVER, result.response.result)
                assertEquals("1", result.response.winnerId)
            } else {
                assertFalse(result.response.gameOver)
            }
        }

        // 6. Verify final game state
        val finalGame = gameService.getGame(gameId)!!
        assertEquals(GameStatus.COMPLETED, finalGame.status)
        assertEquals(1, finalGame.winner)
    }

    @Test
    fun `turn enforcement - firing out of turn rejected`() {
        val game = gameService.createGame(GameMode.MULTIPLAYER)
        val gameId = game.gameId
        val p1Token = game.player1Token
        val p2Token = game.player2Token

        placementService.placeShips(gameId, p1Token, standardPlacements(0))
        placementService.placeShips(gameId, p2Token, standardPlacements(5))

        val currentGame = gameService.getGame(gameId)!!
        assertEquals(GameStatus.IN_PROGRESS, currentGame.status)

        // Determine who goes first and try to fire with the other player
        val wrongToken = if (currentGame.currentTurn == 1) p2Token else p1Token

        val ex = assertThrows<IllegalArgumentException> {
            firingService.fire(gameId, wrongToken, 0, 0)
        }
        assertTrue(ex.message!!.contains("not your turn"))
    }

    @Test
    fun `invalid player token rejected on fire`() {
        val game = gameService.createGame(GameMode.MULTIPLAYER)
        val gameId = game.gameId

        placementService.placeShips(gameId, game.player1Token, standardPlacements(0))
        placementService.placeShips(gameId, game.player2Token, standardPlacements(5))

        assertThrows<IllegalArgumentException> {
            firingService.fire(gameId, "invalid-token", 0, 0)
        }
    }

    @Test
    fun `invalid player token rejected on place ships`() {
        val game = gameService.createGame(GameMode.MULTIPLAYER)

        assertThrows<IllegalArgumentException> {
            placementService.placeShips(game.gameId, "invalid-token", standardPlacements(0))
        }
    }

    @Test
    fun `cannot fire before ships are placed`() {
        val game = gameService.createGame(GameMode.MULTIPLAYER)

        assertThrows<IllegalArgumentException> {
            firingService.fire(game.gameId, game.player1Token, 0, 0)
        }
    }
}
