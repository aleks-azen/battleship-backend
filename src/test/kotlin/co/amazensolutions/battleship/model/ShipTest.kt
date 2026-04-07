package co.amazensolutions.battleship.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShipTest {

    @Test
    fun `occupiedCells horizontal ship returns correct coordinates`() {
        val ship = PlacedShip(ShipType.CRUISER, Coordinate(2, 3), Orientation.HORIZONTAL)
        val cells = ship.occupiedCells()
        assertEquals(
            listOf(Coordinate(2, 3), Coordinate(2, 4), Coordinate(2, 5)),
            cells
        )
    }

    @Test
    fun `occupiedCells vertical ship returns correct coordinates`() {
        val ship = PlacedShip(ShipType.BATTLESHIP, Coordinate(1, 0), Orientation.VERTICAL)
        val cells = ship.occupiedCells()
        assertEquals(
            listOf(Coordinate(1, 0), Coordinate(2, 0), Coordinate(3, 0), Coordinate(4, 0)),
            cells
        )
    }

    @Test
    fun `occupiedCells destroyer has 2 cells`() {
        val ship = PlacedShip(ShipType.DESTROYER, Coordinate(0, 0), Orientation.HORIZONTAL)
        assertEquals(2, ship.occupiedCells().size)
        assertEquals(listOf(Coordinate(0, 0), Coordinate(0, 1)), ship.occupiedCells())
    }

    @Test
    fun `occupiedCells carrier has 5 cells`() {
        val ship = PlacedShip(ShipType.CARRIER, Coordinate(0, 0), Orientation.VERTICAL)
        assertEquals(5, ship.occupiedCells().size)
        assertEquals(Coordinate(4, 0), ship.occupiedCells().last())
    }

    @Test
    fun `isSunk returns true when all cells are hit`() {
        val ship = PlacedShip(ShipType.DESTROYER, Coordinate(0, 0), Orientation.HORIZONTAL)
        val hits = setOf(Coordinate(0, 0), Coordinate(0, 1))
        assertTrue(ship.isSunk(hits))
    }

    @Test
    fun `isSunk returns false when not all cells are hit`() {
        val ship = PlacedShip(ShipType.CRUISER, Coordinate(3, 3), Orientation.HORIZONTAL)
        val hits = setOf(Coordinate(3, 3), Coordinate(3, 4))
        assertFalse(ship.isSunk(hits))
    }

    @Test
    fun `isSunk returns false with no hits`() {
        val ship = PlacedShip(ShipType.BATTLESHIP, Coordinate(0, 0), Orientation.VERTICAL)
        assertFalse(ship.isSunk(emptySet()))
    }

    @Test
    fun `isSunk ignores hits on other coordinates`() {
        val ship = PlacedShip(ShipType.DESTROYER, Coordinate(0, 0), Orientation.HORIZONTAL)
        val hits = setOf(Coordinate(0, 0), Coordinate(5, 5))
        assertFalse(ship.isSunk(hits))
    }

    @Test
    fun `isSunk with superset of hits still returns true`() {
        val ship = PlacedShip(ShipType.DESTROYER, Coordinate(0, 0), Orientation.HORIZONTAL)
        val hits = setOf(Coordinate(0, 0), Coordinate(0, 1), Coordinate(5, 5))
        assertTrue(ship.isSunk(hits))
    }
}
