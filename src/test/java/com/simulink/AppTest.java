package com.simulink;

import static org.junit.Assert.*;

import org.junit.Test;
import com.simulink.model.RouteGrid;
import java.util.List;

/**
 * Unit test for RouteGrid pathfinding and UI logic.
 */
public class AppTest {
    @Test
    public void shouldAnswerWithTrue() {
        assertTrue(true);
    }

    @Test
    public void testStraightPath() {
        RouteGrid grid = new RouteGrid(300, 300, 10);
        // Start (0, 100), End (100, 100) -> should be straight
        List<Double> path = grid.findPath(0, 100, 100, 100);
        assertNotNull(path);
        // Collinear points should be simplified, leaving only start and end
        assertEquals(4, path.size());
        assertEquals(0.0, path.get(0), 1e-9);
        assertEquals(100.0, path.get(1), 1e-9);
        assertEquals(100.0, path.get(2), 1e-9);
        assertEquals(100.0, path.get(3), 1e-9);
    }

    @Test
    public void testOneTurnPath() {
        RouteGrid grid = new RouteGrid(300, 300, 10);
        // Start (0, 100), End (100, 150) -> should require offset turns.
        // The stubs are sc=0, stubOutCol=2 (since GRID_SIZE=20 is 2 cells of
        // cellSize=10), row=10.
        // ec=10, stubInCol=8, row=15.
        // Option A: (2, 10) -> (8, 10) -> (8, 15).
        // The path points are: (0, 100) -> (20, 100) -> (80, 100) -> (80, 150) -> (100,
        // 150)
        // Simplified collinear: (0, 100) -> (80, 100) -> (80, 150) -> (100, 150)
        List<Double> path = grid.findPath(0, 100, 100, 150);
        assertNotNull(path);

        // Let's verify we get a simple Manhattan path (4 bend points / 8 coordinates)
        assertEquals(8, path.size());
        assertEquals(0.0, path.get(0), 1e-9);
        assertEquals(100.0, path.get(1), 1e-9);

        assertEquals(80.0, path.get(2), 1e-9);
        assertEquals(100.0, path.get(3), 1e-9);

        assertEquals(80.0, path.get(4), 1e-9);
        assertEquals(150.0, path.get(5), 1e-9);

        assertEquals(100.0, path.get(6), 1e-9);
        assertEquals(150.0, path.get(7), 1e-9);
    }

    @Test
    public void testTwoTurnPathWithObstacle() {
        RouteGrid grid = new RouteGrid(300, 300, 10);
        // Place a block in the middle that prevents Option A (at col 8) and Option B
        // (at col 2)
        // Let's block column 2
        for (int r = 0; r < grid.rows; r++) {
            grid.blocked[2][r] = true;
        }

        // Start (0, 100), End (100, 150)
        // Option B: (2, 10) -> (2, 15) -> (8, 15) is blocked because col 2 is blocked.
        // So simple path A should run. Let's make sure it still finds a valid path.
        List<Double> path = grid.findPath(0, 100, 100, 150);
        // It should fallback or find Option A (col 8 is clear)
        assertNotNull(path);
    }
}
