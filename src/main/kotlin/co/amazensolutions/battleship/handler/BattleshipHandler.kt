package co.amazensolutions.battleship.handler

import co.amazensolutions.battleship.config.BattleshipModule
import co.amazensolutions.battleship.router.RequestRouter
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent
import com.google.inject.Guice

class BattleshipHandler : RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private val router: RequestRouter

    constructor() {
        val injector = Guice.createInjector(BattleshipModule())
        this.router = injector.getInstance(RequestRouter::class.java)
    }

    constructor(router: RequestRouter) {
        this.router = router
    }

    override fun handleRequest(
        input: APIGatewayProxyRequestEvent,
        context: Context
    ): APIGatewayProxyResponseEvent {
        return router.route(input, context)
    }
}
