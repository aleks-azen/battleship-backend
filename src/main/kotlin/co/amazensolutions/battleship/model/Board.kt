package co.amazensolutions.battleship.model

data class Board(
    val ships: List<PlacedShip> = emptyList(),
    val shots: Set<Coordinate> = emptySet(),
    val hits: Set<Coordinate> = emptySet()
) {
    fun allShipsSunk(): Boolean {
        return ships.isNotEmpty() && ships.all { it.isSunk(hits) }
    }

    fun allShipsPlaced(): Boolean {
        val requiredTypes = ShipType.entries.toSet()
        val placedTypes = ships.map { it.type }.toSet()
        return requiredTypes == placedTypes
    }
}
