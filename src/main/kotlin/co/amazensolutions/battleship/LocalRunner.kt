package co.amazensolutions.battleship

import co.amazensolutions.battleship.handler.BattleshipHandler
import com.amazonaws.services.lambda.runtime.ClientContext
import com.amazonaws.services.lambda.runtime.CognitoIdentity
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.LambdaLogger
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 3000
    val handler = BattleshipHandler()
    val server = HttpServer.create(InetSocketAddress(port), 0)

    server.createContext("/") { exchange ->
        try {
            handleRequest(exchange, handler)
        } catch (e: Exception) {
            System.err.println("Error handling request: ${e.message}")
            e.printStackTrace()
            val body = """{"message":"Internal server error","code":"INTERNAL_ERROR"}""".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(500, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
    }

    server.executor = null
    server.start()
    println("Local server running on http://localhost:$port")
}

private fun handleRequest(exchange: HttpExchange, handler: BattleshipHandler) {
    val method = exchange.requestMethod.uppercase()
    val uri = exchange.requestURI
    val path = uri.path
    val query = uri.rawQuery

    println("$method $path")

    // Build APIGatewayProxyRequestEvent
    val event = APIGatewayProxyRequestEvent()
    event.httpMethod = method
    event.path = path

    // Headers
    val headers = mutableMapOf<String, String>()
    exchange.requestHeaders.forEach { (key, values) ->
        if (values.isNotEmpty()) {
            headers[key] = values.first()
        }
    }
    event.headers = headers

    // Query string parameters
    if (!query.isNullOrBlank()) {
        val queryParams = mutableMapOf<String, String>()
        query.split("&").forEach { param ->
            val parts = param.split("=", limit = 2)
            val key = URLDecoder.decode(parts[0], "UTF-8")
            val value = if (parts.size > 1) URLDecoder.decode(parts[1], "UTF-8") else ""
            queryParams[key] = value
        }
        event.queryStringParameters = queryParams
    }

    // Body
    if (method == "POST" || method == "PUT" || method == "PATCH") {
        val body = exchange.requestBody.bufferedReader().readText()
        if (body.isNotEmpty()) {
            event.body = body
        }
    }

    // Path parameters - extract {id} from /games/{id}/*
    val pathParts = path.split("/").filter { it.isNotEmpty() }
    if (pathParts.size >= 2 && pathParts[0] == "games") {
        event.pathParameters = mapOf("id" to pathParts[1])
    }

    // Invoke handler
    val response = handler.handleRequest(event, LocalContext())

    // Map response back to HTTP
    response.headers?.forEach { (key, value) ->
        exchange.responseHeaders.add(key, value)
    }

    val responseBody = response.body?.toByteArray() ?: ByteArray(0)
    val statusCode = response.statusCode ?: 200

    if (responseBody.isEmpty()) {
        exchange.sendResponseHeaders(statusCode, -1)
    } else {
        exchange.sendResponseHeaders(statusCode, responseBody.size.toLong())
        exchange.responseBody.use { it.write(responseBody) }
    }
}

private class LocalContext : Context {
    override fun getAwsRequestId(): String = "local-request-id"
    override fun getLogGroupName(): String = "local-log-group"
    override fun getLogStreamName(): String = "local-log-stream"
    override fun getFunctionName(): String = "battleship-handler"
    override fun getFunctionVersion(): String = "local"
    override fun getInvokedFunctionArn(): String = "arn:aws:lambda:us-east-1:123456789012:function:battleship-handler"
    override fun getIdentity(): CognitoIdentity? = null
    override fun getClientContext(): ClientContext? = null
    override fun getRemainingTimeInMillis(): Int = 300000
    override fun getMemoryLimitInMB(): Int = 512
    override fun getLogger(): LambdaLogger = object : LambdaLogger {
        override fun log(message: String?) {
            println("[LAMBDA] $message")
        }
        override fun log(message: ByteArray?) {
            println("[LAMBDA] ${message?.let { String(it) }}")
        }
    }
}
