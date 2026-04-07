package co.amazensolutions.battleship.config

data class AppConfig(
    val gamesTable: String
) {
    companion object {
        private fun env(name: String): String = requireNotNull(System.getenv(name)) {
            "$name environment variable is not set"
        }

        fun fromEnvironment(): AppConfig = AppConfig(
            gamesTable = env("GAMES_TABLE")
        )
    }
}
