package co.amazensolutions.battleship.model.persistence

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey

@DynamoDbBean
data class GameRecord(
    @get:DynamoDbPartitionKey
    var gameId: String = "",

    var gameData: String = "",

    var status: String = "",

    var mode: String = "",

    var createdAt: Long = 0,

    var updatedAt: Long = 0,

    var ttl: Long = 0
)
