package co.amazensolutions.battleship.config

import co.amazensolutions.battleship.model.persistence.GameRecord
import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.google.inject.Singleton
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

class BattleshipModule : AbstractModule() {

    override fun configure() {
        bind(AppConfig::class.java).toInstance(AppConfig.fromEnvironment())
    }

    @Provides
    @Singleton
    fun dynamoDbClient(): DynamoDbClient = DynamoDbClient.create()

    @Provides
    @Singleton
    fun dynamoDbEnhancedClient(client: DynamoDbClient): DynamoDbEnhancedClient =
        DynamoDbEnhancedClient.builder()
            .dynamoDbClient(client)
            .build()

    @Provides
    @Singleton
    fun gamesTable(
        enhancedClient: DynamoDbEnhancedClient,
        config: AppConfig
    ): DynamoDbTable<GameRecord> =
        enhancedClient.table(config.gamesTable, TableSchema.fromBean(GameRecord::class.java))
}
