package co.amazensolutions.battleship.model

enum class AiMode {
    HUNT,
    TARGET,
    DESTROY
}

data class AiState(
    val mode: AiMode = AiMode.HUNT,
    val targetHits: List<Coordinate> = emptyList(),
    val destroyDirection: Orientation? = null
)
