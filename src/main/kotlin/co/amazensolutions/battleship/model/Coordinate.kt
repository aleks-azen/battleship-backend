package co.amazensolutions.battleship.model

data class Coordinate(
    val row: Int,
    val col: Int
) {
    init {
        require(row in 0..9) { "Row must be 0-9, got $row" }
        require(col in 0..9) { "Col must be 0-9, got $col" }
    }
}
