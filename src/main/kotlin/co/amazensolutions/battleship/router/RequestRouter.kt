package co.amazensolutions.battleship.router

import co.amazensolutions.battleship.model.CreateGameRequest
import co.amazensolutions.battleship.model.CreateGameResponse
import co.amazensolutions.battleship.model.ErrorResponse
import co.amazensolutions.battleship.model.FireRequest
import co.amazensolutions.battleship.model.FireResponse
import co.amazensolutions.battleship.model.FireResponseWithAi
import co.amazensolutions.battleship.model.GameMode
import co.amazensolutions.battleship.model.GameStateResponse
import co.amazensolutions.battleship.model.GameStatus
import co.amazensolutions.battleship.model.BoardView
import co.amazensolutions.battleship.model.JoinGameResponse
import co.amazensolutions.battleship.model.PlaceShipsRequest
import co.amazensolutions.battleship.model.ShipView
import co.amazensolutions.battleship.service.AiService
import co.amazensolutions.battleship.service.FiringService
import co.amazensolutions.battleship.service.GameService
import co.amazensolutions.battleship.service.PlacementService
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import com.google.gson.GsonBuilder
import com.google.inject.Inject
import com.google.inject.Singleton
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

@Singleton
class RequestRouter @Inject constructor(
    private val gameService: GameService,
    private val placementService: PlacementService,
    private val firingService: FiringService,
    private val aiService: AiService
) {
    private val gson = GsonBuilder().create()
    private val gameLocks = ConcurrentHashMap<String, ReentrantLock>()

    companion object {
        private val GAME_STATE_PATTERN = Regex("/games/[^/]+/state")
        private val GAME_SHIPS_PATTERN = Regex("/games/[^/]+/ships")
        private val GAME_FIRE_PATTERN = Regex("/games/[^/]+/fire")
        private val GAME_JOIN_PATTERN = Regex("/games/[^/]+/join")
        private val GAME_ID_PATTERN = Regex("/games/[^/]+")
    }

    private val corsHeaders = mapOf(
        "Access-Control-Allow-Origin" to "*",
        "Access-Control-Allow-Methods" to "GET,POST,PUT,DELETE,OPTIONS",
        "Access-Control-Allow-Headers" to "Content-Type,X-Player-Token",
        "Content-Type" to "application/json"
    )

    fun route(input: APIGatewayProxyRequestEvent, context: Context): APIGatewayProxyResponseEvent {
        val logger = context.logger
        val method = input.httpMethod?.uppercase() ?: ""
        val path = input.path ?: ""

        logger.log("Routing $method $path")

        return try {
            when {
                method == "OPTIONS" -> response(200, "")
                method == "POST" && path == "/games" -> createGame(input)
                method == "POST" && path.matches(GAME_JOIN_PATTERN) -> withGameLock(input, "/join") { joinGame(it) }
                method == "POST" && path.matches(GAME_SHIPS_PATTERN) -> withGameLock(input, "/ships") { placeShips(it) }
                method == "POST" && path.matches(GAME_FIRE_PATTERN) -> withGameLock(input, "/fire") { fire(it) }
                method == "GET" && path.matches(GAME_STATE_PATTERN) -> getGameState(input)
                method == "GET" && path == "/games/history" -> getHistory()
                method == "DELETE" && path.matches(GAME_ID_PATTERN) -> deleteGame(input)
                else -> response(404, gson.toJson(ErrorResponse("Route not found", "NOT_FOUND")))
            }
        } catch (e: IllegalArgumentException) {
            logger.log("Bad request: ${e.message}")
            response(400, gson.toJson(ErrorResponse(e.message ?: "Bad request", "BAD_REQUEST")))
        } catch (e: IllegalStateException) {
            logger.log("Conflict: ${e.message}")
            response(409, gson.toJson(ErrorResponse(e.message ?: "Conflict", "CONFLICT")))
        } catch (e: Exception) {
            logger.log("Internal error: ${e.message}")
            response(500, gson.toJson(ErrorResponse("Internal server error", "INTERNAL_ERROR")))
        }
    }

    private fun withGameLock(
        input: APIGatewayProxyRequestEvent,
        suffix: String,
        handler: (APIGatewayProxyRequestEvent) -> APIGatewayProxyResponseEvent
    ): APIGatewayProxyResponseEvent {
        val gameId = extractPathParam(input.path, suffix)
        val lock = gameLocks.computeIfAbsent(gameId) { ReentrantLock() }
        lock.lock()
        try {
            return handler(input)
        } finally {
            lock.unlock()
        }
    }

    private fun createGame(input: APIGatewayProxyRequestEvent): APIGatewayProxyResponseEvent {
        val request = gson.fromJson(input.body, CreateGameRequest::class.java)
        var game = gameService.createGame(request.mode)

        if (request.mode == GameMode.SINGLE_PLAYER) {
            game = aiService.placeAiShips(game)
            gameService.saveGame(game)
        }

        val responseBody = CreateGameResponse(
            gameId = game.gameId,
            playerToken = game.player1Token,
            playerNumber = 1,
            status = game.status,
            mode = game.mode
        )
        return response(201, gson.toJson(responseBody))
    }

    private fun joinGame(input: APIGatewayProxyRequestEvent): APIGatewayProxyResponseEvent {
        val gameId = extractPathParam(input.path, "/join")
        val game = gameService.getGame(gameId)
            ?: return response(404, gson.toJson(ErrorResponse("Game not found", "NOT_FOUND")))

        require(game.mode == GameMode.MULTIPLAYER) {
            "Cannot join a single player game"
        }
        require(game.status == GameStatus.PLACING_SHIPS) {
            "Game is not accepting new players"
        }

        val responseBody = JoinGameResponse(
            playerToken = game.player2Token,
            playerNumber = 2
        )
        return response(200, gson.toJson(responseBody))
    }

    private fun getGameState(input: APIGatewayProxyRequestEvent): APIGatewayProxyResponseEvent {
        val gameId = extractPathParam(input.path, "/state")
        val playerToken = requirePlayerToken(input)
        val game = gameService.getGame(gameId)
            ?: return response(404, gson.toJson(ErrorResponse("Game not found", "NOT_FOUND")))

        val playerNumber = try {
            game.resolvePlayerNumber(playerToken)
        } catch (_: IllegalArgumentException) {
            return response(403, gson.toJson(ErrorResponse("Invalid player token", "FORBIDDEN")))
        }

        val playerBoard = game.playerState(playerNumber).board
        val opponentBoard = game.opponentState(playerNumber).board

        val responseBody = GameStateResponse(
            gameId = game.gameId,
            status = game.status,
            mode = game.mode,
            playerBoard = BoardView(
                ships = playerBoard.ships.map { ship ->
                    ShipView(ship.type, ship.origin, ship.orientation, ship.isSunk(playerBoard.hits))
                },
                shots = playerBoard.shots.toList(),
                hits = playerBoard.hits.toList()
            ),
            opponentBoard = BoardView(
                ships = emptyList(),
                shots = opponentBoard.shots.toList(),
                hits = opponentBoard.hits.toList()
            ),
            currentTurn = if (game.currentTurn == playerNumber) "you" else "opponent",
            winnerId = game.winner?.let { if (it == playerNumber) "you" else "opponent" }
        )
        return response(200, gson.toJson(responseBody))
    }

    private fun getHistory(): APIGatewayProxyResponseEvent {
        val games = gameService.listCompletedGames()
        return response(200, gson.toJson(games.map { mapOf("gameId" to it.gameId, "mode" to it.mode, "winner" to it.winner) }))
    }

    private fun placeShips(input: APIGatewayProxyRequestEvent): APIGatewayProxyResponseEvent {
        val gameId = extractPathParam(input.path, "/ships")
        val playerToken = requirePlayerToken(input)
        val request = gson.fromJson(input.body, PlaceShipsRequest::class.java)
        val game = placementService.placeShips(gameId, playerToken, request.ships)
        return response(200, gson.toJson(mapOf("gameId" to game.gameId, "status" to game.status)))
    }

    private fun fire(input: APIGatewayProxyRequestEvent): APIGatewayProxyResponseEvent {
        val gameId = extractPathParam(input.path, "/fire")
        val playerToken = requirePlayerToken(input)
        val request = gson.fromJson(input.body, FireRequest::class.java)
        val humanFireResult = firingService.fire(gameId, playerToken, request.row, request.col)
        val humanResponse = humanFireResult.response

        var aiResponse: FireResponse? = null
        if (!humanResponse.gameOver) {
            val currentGame = humanFireResult.game
            if (currentGame.mode == GameMode.SINGLE_PLAYER && currentGame.currentTurn == 2) {
                val target = aiService.chooseAiTarget(currentGame)
                val aiFireResult = firingService.fire(gameId, currentGame.player2Token, target.row, target.col, isServerAiCall = true)
                aiResponse = aiFireResult.response

                val updatedGame = aiService.updateAiStateAfterShot(
                    aiFireResult.game, target, aiResponse.result, aiResponse.sunkShip
                )
                gameService.saveGame(updatedGame)
            }
        }

        val responseBody = FireResponseWithAi(
            result = humanResponse.result,
            coordinate = humanResponse.coordinate,
            sunkShip = humanResponse.sunkShip,
            gameOver = humanResponse.gameOver || (aiResponse?.gameOver == true),
            winnerId = humanResponse.winnerId ?: aiResponse?.winnerId,
            aiResult = aiResponse
        )
        return response(200, gson.toJson(responseBody))
    }

    private fun deleteGame(input: APIGatewayProxyRequestEvent): APIGatewayProxyResponseEvent {
        val gameId = extractPathParam(input.path, "")
        gameService.deleteGame(gameId)
        return response(204, "")
    }

    private fun extractPathParam(path: String, suffix: String): String {
        val trimmed = path.removeSuffix(suffix)
        return trimmed.split("/").last { it.isNotEmpty() }
    }

    private fun requirePlayerToken(input: APIGatewayProxyRequestEvent): String {
        return input.headers?.get("X-Player-Token")
            ?: throw IllegalArgumentException("X-Player-Token header is required")
    }

    private fun response(statusCode: Int, body: String): APIGatewayProxyResponseEvent {
        return APIGatewayProxyResponseEvent()
            .withStatusCode(statusCode)
            .withHeaders(corsHeaders)
            .withBody(body)
    }
}
