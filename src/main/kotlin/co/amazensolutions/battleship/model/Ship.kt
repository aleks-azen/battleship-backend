package co.amazensolutions.battleship.model

enum class ShipType(val displayName: String, val size: Int) {
    CARRIER("Carrier", 5),
    BATTLESHIP("Battleship", 4),
    CRUISER("Cruiser", 3),
    SUBMARINE("Submarine", 3),
    DESTROYER("Destroyer", 2);
}

data class PlacedShip(
    val type: ShipType,
    val origin: Coordinate,
    val orientation: Orientation
) {
    fun occupiedCells(): List<Coordinate> {
        return (0 until type.size).map { offset ->
            when (orientation) {
                Orientation.HORIZONTAL -> Coordinate(origin.row, origin.col + offset)
                Orientation.VERTICAL -> Coordinate(origin.row + offset, origin.col)
            }
        }
    }

    fun isSunk(hits: Set<Coordinate>): Boolean {
        return occupiedCells().all { it in hits }
    }
}
