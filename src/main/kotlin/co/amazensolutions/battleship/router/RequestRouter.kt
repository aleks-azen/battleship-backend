package co.amazensolutions.battleship.router

import co.amazensolutions.battleship.model.Game
import co.amazensolutions.battleship.model.CreateGameRequest
import co.amazensolutions.battleship.model.CreateGameResponse
import co.amazensolutions.battleship.model.ErrorResponse
import co.amazensolutions.battleship.model.FireRequest
import co.amazensolutions.battleship.model.FireResponse
import co.amazensolutions.battleship.model.FireResponseWithAi
import co.amazensolutions.battleship.model.GameHistoryEntry
import co.amazensolutions.battleship.model.GameMode
import co.amazensolutions.battleship.model.GameStateResponse
import co.amazensolutions.battleship.model.SpectatorGameStateResponse
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
        check(game.status == GameStatus.PLACING_SHIPS) {
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
        val playerToken = optionalPlayerToken(input)
        val game = gameService.getGame(gameId)
            ?: return response(404, gson.toJson(ErrorResponse("Game not found", "NOT_FOUND")))

        val playerNumber = playerToken?.let {
            try {
                game.resolvePlayerNumber(it)
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        if (playerNumber != null) {
            return getPlayerGameState(game, playerNumber, input)
        }

        return getSpectatorGameState(game, input)
    }

    private fun getPlayerGameState(
        game: Game,
        playerNumber: Int,
        input: APIGatewayProxyRequestEvent
    ): APIGatewayProxyResponseEvent {
        val since = input.queryStringParameters?.get("since")?.toLongOrNull()
        if (since != null && game.updatedAt < since) {
            return response(304, "")
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
            currentTurn = toPerspective(game.currentTurn, playerNumber),
            winnerId = game.winner?.let { toPerspective(it, playerNumber) },
            updatedAt = game.updatedAt
        )
        return response(200, gson.toJson(responseBody))
    }

    private fun getSpectatorGameState(
        game: Game,
        input: APIGatewayProxyRequestEvent
    ): APIGatewayProxyResponseEvent {
        val since = input.queryStringParameters?.get("since")?.toLongOrNull()
        if (since != null && game.updatedAt < since) {
            return response(304, "")
        }

        return when (game.status) {
            GameStatus.COMPLETED -> {
                val p1Board = game.player1.board
                val p2Board = game.player2.board
                val responseBody = SpectatorGameStateResponse(
                    gameId = game.gameId,
                    status = game.status,
                    mode = game.mode,
                    player1Board = BoardView(
                        ships = p1Board.ships.map { ship ->
                            ShipView(ship.type, ship.origin, ship.orientation, ship.isSunk(p1Board.hits))
                        },
                        shots = p1Board.shots.toList(),
                        hits = p1Board.hits.toList()
                    ),
                    player2Board = BoardView(
                        ships = p2Board.ships.map { ship ->
                            ShipView(ship.type, ship.origin, ship.orientation, ship.isSunk(p2Board.hits))
                        },
                        shots = p2Board.shots.toList(),
                        hits = p2Board.hits.toList()
                    ),
                    currentTurn = game.currentTurn,
                    winnerId = game.winner,
                    updatedAt = game.updatedAt
                )
                response(200, gson.toJson(responseBody))
            }
            GameStatus.IN_PROGRESS -> {
                val p1Board = game.player1.board
                val p2Board = game.player2.board
                val responseBody = SpectatorGameStateResponse(
                    gameId = game.gameId,
                    status = game.status,
                    mode = game.mode,
                    player1Board = BoardView(
                        ships = emptyList(),
                        shots = p1Board.shots.toList(),
                        hits = p1Board.hits.toList()
                    ),
                    player2Board = BoardView(
                        ships = emptyList(),
                        shots = p2Board.shots.toList(),
                        hits = p2Board.hits.toList()
                    ),
                    currentTurn = game.currentTurn,
                    winnerId = null,
                    updatedAt = game.updatedAt
                )
                response(200, gson.toJson(responseBody))
            }
            GameStatus.PLACING_SHIPS -> {
                val responseBody = SpectatorGameStateResponse(
                    gameId = game.gameId,
                    status = game.status,
                    mode = game.mode,
                    player1Board = BoardView(ships = emptyList(), shots = emptyList(), hits = emptyList()),
                    player2Board = BoardView(ships = emptyList(), shots = emptyList(), hits = emptyList()),
                    currentTurn = game.currentTurn,
                    winnerId = null,
                    updatedAt = game.updatedAt
                )
                response(200, gson.toJson(responseBody))
            }
        }
    }

    private fun getHistory(): APIGatewayProxyResponseEvent {
        val games = gameService.listCompletedGames()
        val entries = games.map { game ->
            GameHistoryEntry(
                gameId = game.gameId,
                mode = game.mode,
                winner = game.winner,
                createdAt = game.createdAt,
                updatedAt = game.updatedAt,
                moveCount = game.totalMoveCount()
            )
        }.sortedByDescending { it.createdAt }
        return response(200, gson.toJson(entries))
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
        val requestingPlayerNumber = humanFireResult.playerNumber

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

        val rawWinnerId = humanResponse.winnerId ?: aiResponse?.winnerId

        val responseBody = FireResponseWithAi(
            result = humanResponse.result,
            coordinate = humanResponse.coordinate,
            sunkShip = humanResponse.sunkShip,
            gameOver = humanResponse.gameOver || (aiResponse?.gameOver == true),
            winnerId = rawWinnerId?.let { toPerspective(it, requestingPlayerNumber) },
            aiResult = aiResponse?.copy(winnerId = null)
        )
        return response(200, gson.toJson(responseBody))
    }

    private fun deleteGame(input: APIGatewayProxyRequestEvent): APIGatewayProxyResponseEvent {
        val gameId = extractPathParam(input.path, "")
        gameService.deleteGame(gameId)
        return response(204, "")
    }

    private fun toPerspective(value: Int, playerNumber: Int): String =
        if (value == playerNumber) "you" else "opponent"

    private fun extractPathParam(path: String, suffix: String): String {
        val trimmed = path.removeSuffix(suffix)
        return trimmed.split("/").last { it.isNotEmpty() }
    }

    private fun requirePlayerToken(input: APIGatewayProxyRequestEvent): String {
        val headers = input.headers ?: throw IllegalArgumentException("X-Player-Token header is required")
        return headers.entries.firstOrNull { it.key.equals("X-Player-Token", ignoreCase = true) }?.value
            ?: throw IllegalArgumentException("X-Player-Token header is required")
    }

    private fun optionalPlayerToken(input: APIGatewayProxyRequestEvent): String? {
        return input.headers?.entries?.firstOrNull { it.key.equals("X-Player-Token", ignoreCase = true) }?.value
    }

    private fun response(statusCode: Int, body: String): APIGatewayProxyResponseEvent {
        return APIGatewayProxyResponseEvent()
            .withStatusCode(statusCode)
            .withHeaders(corsHeaders)
            .withBody(body)
    }
}
