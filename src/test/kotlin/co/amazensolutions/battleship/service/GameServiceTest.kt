package co.amazensolutions.battleship.service

import co.amazensolutions.battleship.model.Game
import co.amazensolutions.battleship.model.GameMode
import co.amazensolutions.battleship.model.GameStatus
import co.amazensolutions.battleship.model.persistence.GameRecord
import com.google.gson.GsonBuilder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable
import software.amazon.awssdk.enhanced.dynamodb.Key
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameServiceTest {

    private lateinit var gamesTable: DynamoDbTable<GameRecord>
    private lateinit var gameService: GameService
    private val gson = GsonBuilder().create()

    @BeforeEach
    fun setup() {
        @Suppress("UNCHECKED_CAST")
        gamesTable = mockk<DynamoDbTable<GameRecord>>(relaxed = true)
        gameService = GameService(gamesTable)
    }

    @Test
    fun `createGame single player generates UUID for both tokens`() {
        val recordSlot = slot<GameRecord>()
        every { gamesTable.putItem(capture(recordSlot)) } returns Unit

        val game = gameService.createGame(GameMode.SINGLE_PLAYER)

        assertEquals(GameMode.SINGLE_PLAYER, game.mode)
        assertEquals(GameStatus.PLACING_SHIPS, game.status)
        assertTrue(game.player2Token.isNotBlank())
        assertTrue(game.player2Token != "AI")
        assertEquals(1, game.currentTurn)
        assertNull(game.winner)
        assertNotNull(game.gameId)
        assertNotNull(game.player1Token)
        assertTrue(game.player1Token != game.player2Token)

        verify { gamesTable.putItem(any<GameRecord>()) }
        val saved = recordSlot.captured
        assertEquals(game.gameId, saved.gameId)
        assertEquals("PLACING_SHIPS", saved.status)
        assertEquals("SINGLE_PLAYER", saved.mode)
        assertNotNull(saved.ttl)
        assertTrue(saved.ttl!! > 0)
    }

    @Test
    fun `createGame multiplayer generates both player tokens`() {
        every { gamesTable.putItem(any<GameRecord>()) } returns Unit

        val game = gameService.createGame(GameMode.MULTIPLAYER)

        assertEquals(GameMode.MULTIPLAYER, game.mode)
        assertTrue(game.player1Token.isNotBlank())
        assertTrue(game.player2Token.isNotBlank())
        assertTrue(game.player1Token != game.player2Token)
        assertTrue(game.player2Token != "AI")
    }

    @Test
    fun `getGame returns null when record not found`() {
        every { gamesTable.getItem(any<Key>()) } returns null

        val result = gameService.getGame("nonexistent")

        assertNull(result)
    }

    @Test
    fun `getGame deserializes game from record`() {
        val game = Game(
            gameId = "test-id",
            mode = GameMode.SINGLE_PLAYER,
            status = GameStatus.IN_PROGRESS,
            currentTurn = 1,
            player1Token = "p1-token",
            player2Token = "p2-token"
        )
        val record = GameRecord(
            gameId = "test-id",
            gameData = gson.toJson(game),
            status = "IN_PROGRESS",
            mode = "SINGLE_PLAYER",
            createdAt = game.createdAt,
            updatedAt = game.updatedAt
        )
        every { gamesTable.getItem(any<Key>()) } returns record

        val result = gameService.getGame("test-id")

        assertNotNull(result)
        assertEquals("test-id", result.gameId)
        assertEquals(GameMode.SINGLE_PLAYER, result.mode)
        assertEquals(GameStatus.IN_PROGRESS, result.status)
        assertEquals("p1-token", result.player1Token)
        assertEquals("p2-token", result.player2Token)
    }

    @Test
    fun `saveGame serializes and stores record with TTL for active games`() {
        val recordSlot = slot<GameRecord>()
        every { gamesTable.putItem(capture(recordSlot)) } returns Unit

        val game = Game(
            gameId = "game-123",
            mode = GameMode.SINGLE_PLAYER,
            status = GameStatus.IN_PROGRESS,
            player1Token = "p1",
            player2Token = "p2"
        )

        gameService.saveGame(game)

        val saved = recordSlot.captured
        assertEquals("game-123", saved.gameId)
        assertEquals("IN_PROGRESS", saved.status)
        assertEquals("SINGLE_PLAYER", saved.mode)
        assertNotNull(saved.ttl)
        assertTrue(saved.ttl!! > 0)
        assertTrue(saved.gameData.contains("game-123"))

        val deserialized = gson.fromJson(saved.gameData, Game::class.java)
        assertEquals("game-123", deserialized.gameId)
    }

    @Test
    fun `saveGame updates updatedAt timestamp`() {
        val recordSlot = slot<GameRecord>()
        every { gamesTable.putItem(capture(recordSlot)) } returns Unit

        val oldTime = System.currentTimeMillis() - 10000
        val game = Game(
            gameId = "game-123",
            mode = GameMode.SINGLE_PLAYER,
            status = GameStatus.PLACING_SHIPS,
            player1Token = "p1",
            player2Token = "p2",
            createdAt = oldTime,
            updatedAt = oldTime
        )

        gameService.saveGame(game)

        val saved = recordSlot.captured
        assertEquals(oldTime, saved.createdAt)
        assertTrue(saved.updatedAt > oldTime)
    }

    @Test
    fun `listCompletedGames returns deserialized games`() {
        val game1 = Game(
            gameId = "g1",
            mode = GameMode.SINGLE_PLAYER,
            status = GameStatus.COMPLETED,
            player1Token = "p1",
            player2Token = "p2-ai",
            winner = 1
        )
        val game2 = Game(
            gameId = "g2",
            mode = GameMode.MULTIPLAYER,
            status = GameStatus.COMPLETED,
            player1Token = "p1",
            player2Token = "p2",
            winner = 2
        )

        val records = listOf(
            GameRecord(gameId = "g1", gameData = gson.toJson(game1), status = "COMPLETED", mode = "SINGLE_PLAYER"),
            GameRecord(gameId = "g2", gameData = gson.toJson(game2), status = "COMPLETED", mode = "MULTIPLAYER")
        )

        val sdkIterable = mockk<software.amazon.awssdk.core.pagination.sync.SdkIterable<GameRecord>>()
        every { sdkIterable.iterator() } returns records.toMutableList().iterator()
        val scanResult = mockk<software.amazon.awssdk.enhanced.dynamodb.model.PageIterable<GameRecord>>()
        every { scanResult.items() } returns sdkIterable
        every { gamesTable.scan(any<ScanEnhancedRequest>()) } returns scanResult

        val result = gameService.listCompletedGames()

        assertEquals(2, result.size)
        assertEquals("g1", result[0].gameId)
        assertEquals("g2", result[1].gameId)
        assertEquals(1, result[0].winner)
        assertEquals(2, result[1].winner)
    }

    @Test
    fun `deleteGame calls deleteItem with correct key`() {
        every { gamesTable.deleteItem(any<Key>()) } returns null

        gameService.deleteGame("game-to-delete")

        val keySlot = slot<Key>()
        verify { gamesTable.deleteItem(capture(keySlot)) }
    }
}
