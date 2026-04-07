package co.amazensolutions.battleship.service

import co.amazensolutions.battleship.model.FireResponse
import co.amazensolutions.battleship.model.Game
import co.amazensolutions.battleship.model.ShipPlacement
import com.google.inject.Inject
import com.google.inject.Singleton

@Singleton
class AiService @Inject constructor(
    private val gameService: GameService
) {

    fun placeAiShips(gameId: String): Game {
        TODO("AI ship placement not yet implemented")
    }

    fun aiTurn(gameId: String): FireResponse {
        TODO("AI firing logic not yet implemented")
    }
}
