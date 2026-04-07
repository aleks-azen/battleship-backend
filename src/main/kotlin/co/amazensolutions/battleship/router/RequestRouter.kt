package co.amazensolutions.battleship.router

import co.amazensolutions.battleship.model.CreateGameRequest
import co.amazensolutions.battleship.model.CreateGameResponse
import co.amazensolutions.battleship.model.ErrorResponse
import co.amazensolutions.battleship.model.FireRequest
import co.amazensolutions.battleship.model.GameStateResponse
import co.amazensolutions.battleship.model.BoardView
import co.amazensolutions.battleship.model.PlaceShipsRequest
import co.amazensolutions.battleship.model.ShipView
import co.amazensolutions.battleship.service.AiService
import co.amazensolutions.battleship.service.FiringService
import co.amazensolutions.battleship.service.GameService
import co.amazensolutions.battleship.service.PlacementService
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import com.google.gson.Gson
import com.google.inject.Inject
import com.google.inject.Singleton

@Singleton
class RequestRouter @Inject constructor(
    private val gameService: GameService,
    private val placementService: PlacementService,
    private val firingService: FiringService,
    private val aiService: AiService
) {
    private val gson = Gson()

    private val corsHeaders = mapOf(
        "Access-Control-Allow-Origin" to "*",
        "Access-Control-Allow-Methods" to "GET,POST,PUT,DELETE,OPTIONS",
        "Access-Control-Allow-Headers" to "Content-Type,Authorization",
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
                method == "GET" && path.matches(Regex("/games/[^/]+")) -> getGame(input)
                method == "POST" && path.matches(Regex("/games/[^/]+/ships")) -> placeShips(input)
                method == "POST" && path.matches(Regex("/games/[^/]+/fire")) -> fire(input)
                method == "DELETE" && path.matches(Regex("/games/[^/]+")) -> deleteGame(input)
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

    private fun createGame(input: APIGatewayProxyRequestEvent): APIGatewayProxyResponseEvent {
        val request = gson.fromJson(input.body, CreateGameRequest::class.java)
        val game = gameService.createGame(request.mode)
        val responseBody = CreateGameResponse(
            gameId = game.gameId,
            status = game.status,
            mode = game.mode
        )
        return response(201, gson.toJson(responseBody))
    }

    private fun getGame(input: APIGatewayProxyRequestEvent): APIGatewayProxyResponseEvent {
        val gameId = extractGameId(input.path)
        val game = gameService.getGame(gameId)
        val responseBody = GameStateResponse(
            gameId = game.gameId,
            status = game.status,
            mode = game.mode,
            playerBoard = BoardView(
                ships = game.playerBoard.ships.map { ship ->
                    ShipView(ship.type, ship.origin, ship.orientation, ship.isSunk(game.playerBoard.hits))
                },
                shots = game.playerBoard.shots.toList(),
                hits = game.playerBoard.hits.toList()
            ),
            opponentBoard = BoardView(
                ships = emptyList(),
                shots = game.opponentBoard.shots.toList(),
                hits = game.opponentBoard.hits.toList()
            ),
            currentTurn = game.currentTurn,
            winnerId = game.winnerId
        )
        return response(200, gson.toJson(responseBody))
    }

    private fun placeShips(input: APIGatewayProxyRequestEvent): APIGatewayProxyResponseEvent {
        val gameId = extractGameId(input.path.removeSuffix("/ships"))
        val request = gson.fromJson(input.body, PlaceShipsRequest::class.java)
        val game = placementService.placeShips(gameId, request.ships)
        return response(200, gson.toJson(mapOf("gameId" to game.gameId, "status" to game.status)))
    }

    private fun fire(input: APIGatewayProxyRequestEvent): APIGatewayProxyResponseEvent {
        val gameId = extractGameId(input.path.removeSuffix("/fire"))
        val request = gson.fromJson(input.body, FireRequest::class.java)
        val result = firingService.fire(gameId, request.row, request.col)
        return response(200, gson.toJson(result))
    }

    private fun deleteGame(input: APIGatewayProxyRequestEvent): APIGatewayProxyResponseEvent {
        val gameId = extractGameId(input.path)
        gameService.deleteGame(gameId)
        return response(204, "")
    }

    private fun extractGameId(path: String): String {
        return path.split("/").last { it.isNotEmpty() }
    }

    private fun response(statusCode: Int, body: String): APIGatewayProxyResponseEvent {
        return APIGatewayProxyResponseEvent()
            .withStatusCode(statusCode)
            .withHeaders(corsHeaders)
            .withBody(body)
    }
}
