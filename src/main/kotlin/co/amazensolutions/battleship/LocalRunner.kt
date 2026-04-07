package co.amazensolutions.battleship

import co.amazensolutions.battleship.handler.BattleshipHandler
import com.amazonaws.services.lambda.runtime.ClientContext
import com.amazonaws.services.lambda.runtime.CognitoIdentity
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.LambdaLogger
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import java.io.File

fun main(args: Array<String>) {
    val eventFile = args.firstOrNull() ?: "events/create-game.json"
    val eventJson = File(eventFile).readText()

    val event = com.google.gson.Gson().fromJson(eventJson, APIGatewayProxyRequestEvent::class.java)
    val handler = BattleshipHandler()
    val response = handler.handleRequest(event, LocalContext())

    println("Status: ${response.statusCode}")
    println("Body: ${response.body}")
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
