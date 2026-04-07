package co.amazensolutions.battleship.service

import co.amazensolutions.battleship.model.Board
import co.amazensolutions.battleship.model.Game
import co.amazensolutions.battleship.model.GameMode
import co.amazensolutions.battleship.model.GameStatus
import co.amazensolutions.battleship.model.persistence.GameRecord
import com.google.gson.Gson
import com.google.inject.Inject
import com.google.inject.Singleton
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable
import software.amazon.awssdk.enhanced.dynamodb.Key
import java.util.UUID

@Singleton
class GameService @Inject constructor(
    private val gamesTable: DynamoDbTable<GameRecord>
) {
    private val gson = Gson()
    private val ttlDuration = 24 * 60 * 60L // 24 hours in seconds

    fun createGame(mode: GameMode): Game {
        val game = Game(
            gameId = UUID.randomUUID().toString(),
            mode = mode,
            status = GameStatus.PLACING_SHIPS,
            playerBoard = Board(),
            opponentBoard = Board()
        )
        saveGame(game)
        return game
    }

    fun getGame(gameId: String): Game {
        val record = gamesTable.getItem(
            Key.builder().partitionValue(gameId).build()
        ) ?: throw IllegalArgumentException("Game not found: $gameId")
        return deserializeGame(record)
    }

    fun saveGame(game: Game) {
        val now = System.currentTimeMillis()
        val record = GameRecord(
            gameId = game.gameId,
            gameData = gson.toJson(game),
            status = game.status.name,
            mode = game.mode.name,
            createdAt = game.createdAt,
            updatedAt = now,
            ttl = (now / 1000) + ttlDuration
        )
        gamesTable.putItem(record)
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
