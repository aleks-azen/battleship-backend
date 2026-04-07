package co.amazensolutions.battleship.model

data class PlayerState(
    val board: Board = Board(),
    val shipsPlaced: Boolean = false
)
