package co.amazensolutions.battleship.model

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BoardTest {

    @Test
    fun `allShipsSunk returns true when all ships are sunk`() {
        val ship1 = PlacedShip(ShipType.DESTROYER, Coordinate(0, 0), Orientation.HORIZONTAL)
        val ship2 = PlacedShip(ShipType.SUBMARINE, Coordinate(2, 0), Orientation.HORIZONTAL)
        val hits = setOf(
            Coordinate(0, 0), Coordinate(0, 1),
            Coordinate(2, 0), Coordinate(2, 1), Coordinate(2, 2)
        )
        val board = Board(ships = listOf(ship1, ship2), shots = hits, hits = hits)
        assertTrue(board.allShipsSunk())
    }

    @Test
    fun `allShipsSunk returns false when some ships remain`() {
        val ship1 = PlacedShip(ShipType.DESTROYER, Coordinate(0, 0), Orientation.HORIZONTAL)
        val ship2 = PlacedShip(ShipType.SUBMARINE, Coordinate(2, 0), Orientation.HORIZONTAL)
        val hits = setOf(Coordinate(0, 0), Coordinate(0, 1))
        val board = Board(ships = listOf(ship1, ship2), shots = hits, hits = hits)
        assertFalse(board.allShipsSunk())
    }

    @Test
    fun `allShipsSunk returns false with no ships`() {
        val board = Board()
        assertFalse(board.allShipsSunk())
    }

    @Test
    fun `allShipsPlaced returns true when all 5 ship types present`() {
        val ships = listOf(
            PlacedShip(ShipType.CARRIER, Coordinate(0, 0), Orientation.HORIZONTAL),
            PlacedShip(ShipType.BATTLESHIP, Coordinate(1, 0), Orientation.HORIZONTAL),
            PlacedShip(ShipType.CRUISER, Coordinate(2, 0), Orientation.HORIZONTAL),
            PlacedShip(ShipType.SUBMARINE, Coordinate(3, 0), Orientation.HORIZONTAL),
            PlacedShip(ShipType.DESTROYER, Coordinate(4, 0), Orientation.HORIZONTAL)
        )
        val board = Board(ships = ships)
        assertTrue(board.allShipsPlaced())
    }

    @Test
    fun `allShipsPlaced returns false when ships are missing`() {
        val ships = listOf(
            PlacedShip(ShipType.CARRIER, Coordinate(0, 0), Orientation.HORIZONTAL)
        )
        val board = Board(ships = ships)
        assertFalse(board.allShipsPlaced())
    }

    @Test
    fun `allShipsPlaced returns false for empty board`() {
        assertFalse(Board().allShipsPlaced())
    }
}
