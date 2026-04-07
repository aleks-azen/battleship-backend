package co.amazensolutions.battleship.service

import co.amazensolutions.battleship.model.Game
import co.amazensolutions.battleship.model.GameMode
import co.amazensolutions.battleship.model.GameStatus
import co.amazensolutions.battleship.model.PlayerState
import co.amazensolutions.battleship.model.persistence.GameRecord
import com.google.gson.GsonBuilder
import com.google.inject.Inject
import com.google.inject.Singleton
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable
import software.amazon.awssdk.enhanced.dynamodb.Key
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import java.util.UUID

@Singleton
class GameService @Inject constructor(
    private val gamesTable: DynamoDbTable<GameRecord>
) {
    private val gson = GsonBuilder().create()
    private val ttlDuration = 24 * 60 * 60L // 24 hours in seconds

    fun createGame(mode: GameMode): Game {
        val now = System.currentTimeMillis()
        val game = Game(
            gameId = UUID.randomUUID().toString(),
            mode = mode,
            status = GameStatus.PLACING_SHIPS,
            player1 = PlayerState(),
            player2 = PlayerState(),
            currentTurn = 1,
            player1Token = UUID.randomUUID().toString(),
            player2Token = UUID.randomUUID().toString(),
            createdAt = now,
            updatedAt = now
        )
        saveGame(game)
        return game
    }

    fun getGame(gameId: String): Game? {
        val record = gamesTable.getItem(
            Key.builder().partitionValue(gameId).build()
        ) ?: return null
        return deserializeGame(record)
    }

    fun saveGame(game: Game) {
        val now = System.currentTimeMillis()
        val updatedGame = game.copy(updatedAt = now)
        val record = GameRecord(
            gameId = updatedGame.gameId,
            gameData = gson.toJson(updatedGame),
            status = updatedGame.status.name,
            mode = updatedGame.mode.name,
            createdAt = updatedGame.createdAt,
            updatedAt = now,
            ttl = if (updatedGame.status == GameStatus.COMPLETED) null else (now / 1000) + ttlDuration
        )
        gamesTable.putItem(record)
    }

    fun listCompletedGames(): List<Game> {
        val scanRequest = ScanEnhancedRequest.builder()
            .filterExpression(
                software.amazon.awssdk.enhanced.dynamodb.Expression.builder()
                    .expression("#s = :status")
                    .putExpressionName("#s", "status")
                    .putExpressionValue(":status", AttributeValue.builder().s(GameStatus.COMPLETED.name).build())
                    .build()
            )
            .build()

        return gamesTable.scan(scanRequest)
            .items()
            .map { deserializeGame(it) }
    }

    fun deleteGame(gameId: String) {
        gamesTable.deleteItem(
            Key.builder().partitionValue(gameId).build()
        )
    }

    private fun deserializeGame(record: GameRecord): Game {
        return gson.fromJson(record.gameData, Game::class.java)
    }
}
