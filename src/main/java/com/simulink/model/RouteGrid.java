package com.simulink.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Grid-based obstacle-aware orthogonal wire router (A*).
 *
 * The canvas is divided into a grid of cells. Blocks mark their occupied
 * cells (plus a small margin) as obstacles. findPath() then searches for
 * the lowest-cost 4-directional (no diagonals) path between two pixel
 * points, preferring straight runs over zig-zags via a turn penalty, and
 * returns a simplified list of bend points only (no redundant collinear
 * coordinate points for optional orthogonal routing.
 *
 * Output ports always exit horizontally (to the right) and input ports
 * always receive horizontally (from the left) — this is enforced by
 * routing between "stub" cells one step out from the real start/end
 * points, rather than by tracking direction constraints inside the search.
 */
public class RouteGrid {
    public final int cols, rows;
    public final double cellSize;
    public final boolean[][] blocked; // [col][row]
    private final double[][] wireCost; // soft cost from wires already routed this pass

    private static final int[] DX = { 1, -1, 0, 0 }; // right, left, down, up
    private static final int[] DY = { 0, 0, 1, -1 };
    private static final double TURN_PENALTY = 50.0; // heavily penalize bends to keep wires straight
    private static final double WIRE_OVERLAP_PENALTY = 1.2; // softly nudges wires apart, without forcing large detours
                                                            // around busy corridors

    public RouteGrid(double canvasWidth, double canvasHeight, double cellSize) {
        this.cellSize = cellSize;
        this.cols = (int) Math.ceil(canvasWidth / cellSize) + 1;
        this.rows = (int) Math.ceil(canvasHeight / cellSize) + 1;
        this.blocked = new boolean[cols][rows];
        this.wireCost = new double[cols][rows];
    }

    public int toCol(double x) {
        return (int) Math.round(x / cellSize);
    }

    public int toRow(double y) {
        return (int) Math.round(y / cellSize);
    }

    public double toX(int col) {
        return col * cellSize;
    }

    public double toY(int row) {
        return row * cellSize;
    }

    public boolean inBounds(int c, int r) {
        return c >= 0 && c < cols && r >= 0 && r < rows;
    }

    public void clear() {
        for (boolean[] col : blocked)
            Arrays.fill(col, false);
    }

    /**
     * Resets the soft wire-usage cost. Call once before rerouting all wires in a
     * pass.
     */
    public void clearUsage() {
        for (double[] col : wireCost)
            Arrays.fill(col, 0);
    }

    /**
     * Adds a small cost to every grid cell a just-routed wire passed through,
     * so the next wire routed in this pass mildly prefers a different
     * corridor when one is available - without forbidding sharing outright,
     * since wires are never hard obstacles for each other.
     */
    public void markUsed(List<Double> pathPoints, double weight) {
        for (int i = 0; i + 3 < pathPoints.size(); i += 2) {
            double x1 = pathPoints.get(i), y1 = pathPoints.get(i + 1);
            double x2 = pathPoints.get(i + 2), y2 = pathPoints.get(i + 3);
            int c1 = toCol(x1), r1 = toRow(y1);
            int c2 = toCol(x2), r2 = toRow(y2);
            if (r1 == r2) {
                int lo = Math.min(c1, c2), hi = Math.max(c1, c2);
                for (int c = lo; c <= hi; c++) {
                    if (inBounds(c, r1))
                        wireCost[c][r1] += weight;
                }
            } else if (c1 == c2) {
                int lo = Math.min(r1, r2), hi = Math.max(r1, r2);
                for (int r = lo; r <= hi; r++) {
                    if (inBounds(c1, r))
                        wireCost[c1][r] += weight;
                }
            }
        }
    }

    /**
     * Mark all cells overlapping a block's bounding box (with margin) as obstacles.
     */
    public void blockRect(double x, double y, double width, double height, double margin) {
        int c0 = toCol(x - margin);
        int c1 = toCol(x + width + margin);
        int r0 = toRow(y - margin);
        int r1 = toRow(y + height + margin);
        for (int c = c0; c <= c1; c++) {
            for (int r = r0; r <= r1; r++) {
                if (inBounds(c, r))
                    blocked[c][r] = true;
            }
        }
    }

    // ---------------- A* search node ----------------

    private static final class Node implements Comparable<Node> {
        final int col, row;
        final int dir; // direction used to arrive at this node, -1 = start
        final double g; // cost so far
        final double f; // g + heuristic
        final Node parent;

        Node(int col, int row, int dir, double g, double f, Node parent) {
            this.col = col;
            this.row = row;
            this.dir = dir;
            this.g = g;
            this.f = f;
            this.parent = parent;
        }

        @Override
        public int compareTo(Node o) {
            return Double.compare(this.f, o.f);
        }
    }

    private double heuristic(int c, int r, int gc, int gr) {
        return Math.abs(c - gc) + Math.abs(r - gr); // Manhattan distance
    }

    private long stateKey(int c, int r, int dir) {
        return (((long) c) << 34) | (((long) r) << 4) | (dir + 1);
    }

    /**
     * A* search over the grid from (startCol,startRow) to (goalCol,goalRow),
     * 4-directional only, avoiding blocked cells. Turning costs extra so the
     * search favors long straight runs (clean bends) over shortest-hop paths
     * that zig-zag. Returns the path as a list of {col,row} cells, or null
     * if no path exists.
     */
    private List<int[]> aStar(int startCol, int startRow, int goalCol, int goalRow) {
        if (!inBounds(startCol, startRow) || !inBounds(goalCol, goalRow))
            return null;
        if (blocked[goalCol][goalRow])
            return null;

        Map<Long, Double> bestG = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>();

        Node start = new Node(startCol, startRow, -1, 0, heuristic(startCol, startRow, goalCol, goalRow), null);
        open.add(start);
        bestG.put(stateKey(startCol, startRow, -1), 0.0);

        int maxIterations = cols * rows * 4 + 16;
        int iterations = 0;

        while (!open.isEmpty() && iterations++ < maxIterations) {
            Node current = open.poll();

            if (current.col == goalCol && current.row == goalRow) {
                return reconstruct(current);
            }

            Double knownG = bestG.get(stateKey(current.col, current.row, current.dir));
            if (knownG != null && current.g > knownG + 1e-9)
                continue; // stale queue entry

            for (int d = 0; d < 4; d++) {
                int nc = current.col + DX[d];
                int nr = current.row + DY[d];
                if (!inBounds(nc, nr) || blocked[nc][nr])
                    continue;

                double stepCost = 1.0;
                if (current.dir != -1 && current.dir != d) {
                    stepCost += TURN_PENALTY; // penalize direction changes
                }
                stepCost += wireCost[nc][nr] * WIRE_OVERLAP_PENALTY; // softly avoid busy corridors

                double ng = current.g + stepCost;
                long key = stateKey(nc, nr, d);
                Double existing = bestG.get(key);
                if (existing == null || ng < existing - 1e-9) {
                    bestG.put(key, ng);
                    double nf = ng + heuristic(nc, nr, goalCol, goalRow);
                    open.add(new Node(nc, nr, d, ng, nf, current));
                }
            }
        }

        return null; // no path found within search budget
    }

    private List<int[]> reconstruct(Node end) {
        LinkedList<int[]> path = new LinkedList<>();
        for (Node n = end; n != null; n = n.parent) {
            path.addFirst(new int[] { n.col, n.row });
        }
        return path;
    }

    /**
     * Finds an orthogonal, obstacle-avoiding path in pixel coordinates from
     * (startX,startY) — an output port — to (endX,endY) — an input port.
     * The path always leaves the start moving right (matching a port on a
     * block's right edge) and always arrives at the end moving right
     * (matching an input port on a block's left edge), by routing between
     * one-cell "stubs" just outside each port rather than the ports
     * themselves. The returned list is already simplified to bend points
     * only: [x1,y1, x2,y2, ...].
     */
    public List<Double> findPath(double startX, double startY, double endX, double endY) {
        int sc = toCol(startX);
        int sr = toRow(startY);
        int ec = toCol(endX);
        int er = toRow(endY);

        // --- Snap all coordinates to exact grid intersections ---
        double snappedStartX = toX(sc);
        double snappedStartY = toY(sr);
        double snappedEndX = toX(ec);
        double snappedEndY = toY(er);

        List<double[]> pathPoints = getSimplePath(startX, startY, endX, endY);
        if (pathPoints == null) {
            // Fallback to A*
            int stubSize = 2; // 20 pixels
            if (ec - sc < 4) {
                stubSize = Math.max(1, (ec - sc) / 2);
            }
            int stubOutCol = Math.max(0, Math.min(cols - 1, sc + stubSize));
            int stubInCol = Math.max(0, Math.min(cols - 1, ec - stubSize));

            List<int[]> gridPath = aStar(stubOutCol, sr, stubInCol, er);

            pathPoints = new ArrayList<>();
            pathPoints.add(new double[] { snappedStartX, snappedStartY });

            if (gridPath == null) {
                // Fallback: straight stub-to-stub run
                pathPoints.add(new double[] { toX(stubOutCol), toY(sr) });
                pathPoints.add(new double[] { toX(stubInCol), toY(er) });
            } else {
                for (int[] cell : gridPath) {
                    pathPoints.add(new double[] { toX(cell[0]), toY(cell[1]) });
                }
            }

            pathPoints.add(new double[] { snappedEndX, snappedEndY });
        }

        List<double[]> simplified = simplify(pathPoints);

        List<Double> flat = new ArrayList<>();
        for (double[] p : simplified) {
            flat.add(p[0]);
            flat.add(p[1]);
        }
        return flat;
    }

    private boolean isSegmentClear(int c1, int r1, int c2, int r2) {
        if (c1 == c2) {
            int minR = Math.min(r1, r2);
            int maxR = Math.max(r1, r2);
            for (int r = minR; r <= maxR; r++) {
                if (!inBounds(c1, r) || blocked[c1][r]) {
                    return false;
                }
            }
        } else if (r1 == r2) {
            int minC = Math.min(c1, c2);
            int maxC = Math.max(c1, c2);
            for (int c = minC; c <= maxC; c++) {
                if (!inBounds(c, r1) || blocked[c][r1]) {
                    return false;
                }
            }
        } else {
            return false;
        }
        return true;
    }

    private List<double[]> getSimplePath(double startX, double startY, double endX, double endY) {
        int sc = toCol(startX);
        int sr = toRow(startY);
        int ec = toCol(endX);
        int er = toRow(endY);

        double snappedStartX = toX(sc);
        double snappedStartY = toY(sr);
        double snappedEndX = toX(ec);
        double snappedEndY = toY(er);

        // Visual grid size is 20, which is 2 cells of cellSize=10.
        // Let's use 2 cells of stub unless sc & ec are closer.
        int stubSize = 2; // 20 pixels
        if (ec - sc < 4) {
            stubSize = Math.max(1, (ec - sc) / 2);
        }
        int stubOutCol = Math.max(0, Math.min(cols - 1, sc + stubSize));
        int stubInCol = Math.max(0, Math.min(cols - 1, ec - stubSize));

        // 1. Straight path: sr == er (and to the right: stubOutCol <= stubInCol)
        if (sr == er && stubOutCol <= stubInCol) {
            if (isSegmentClear(stubOutCol, sr, stubInCol, er)) {
                List<double[]> pts = new ArrayList<>();
                pts.add(new double[] { snappedStartX, snappedStartY });
                pts.add(new double[] { snappedEndX, snappedEndY });
                return pts;
            }
        }

        // 2. 1-Turn paths
        // Option A: (stubOutCol, sr) -> (stubInCol, sr) -> (stubInCol, er)
        if (stubOutCol <= stubInCol &&
                isSegmentClear(stubOutCol, sr, stubInCol, sr) &&
                isSegmentClear(stubInCol, sr, stubInCol, er)) {
            List<double[]> pts = new ArrayList<>();
            pts.add(new double[] { snappedStartX, snappedStartY });
            pts.add(new double[] { toX(stubOutCol), toY(sr) });
            pts.add(new double[] { toX(stubInCol), toY(sr) });
            pts.add(new double[] { toX(stubInCol), toY(er) });
            pts.add(new double[] { snappedEndX, snappedEndY });
            return pts;
        }

        // Option B: (stubOutCol, sr) -> (stubOutCol, er) -> (stubInCol, er)
        if (stubOutCol <= stubInCol &&
                isSegmentClear(stubOutCol, sr, stubOutCol, er) &&
                isSegmentClear(stubOutCol, er, stubInCol, er)) {
            List<double[]> pts = new ArrayList<>();
            pts.add(new double[] { snappedStartX, snappedStartY });
            pts.add(new double[] { toX(stubOutCol), toY(sr) });
            pts.add(new double[] { toX(stubOutCol), toY(er) });
            pts.add(new double[] { toX(stubInCol), toY(er) });
            pts.add(new double[] { snappedEndX, snappedEndY });
            return pts;
        }

        // 3. 2-Turn paths: try to find a column x between stubOutCol and stubInCol
        if (stubOutCol < stubInCol) {
            int minC = stubOutCol;
            int maxC = stubInCol;
            int mid = (minC + maxC) / 2;

            for (int offset = 0; offset <= (maxC - minC); offset++) {
                int[] candidates = { mid - offset, mid + offset };
                for (int x : candidates) {
                    if (x < minC || x > maxC)
                        continue;

                    if (isSegmentClear(stubOutCol, sr, x, sr) &&
                            isSegmentClear(x, sr, x, er) &&
                            isSegmentClear(x, er, stubInCol, er)) {

                        List<double[]> pts = new ArrayList<>();
                        pts.add(new double[] { snappedStartX, snappedStartY });
                        pts.add(new double[] { toX(stubOutCol), toY(sr) });
                        pts.add(new double[] { toX(x), toY(sr) });
                        pts.add(new double[] { toX(x), toY(er) });
                        pts.add(new double[] { toX(stubInCol), toY(er) });
                        pts.add(new double[] { snappedEndX, snappedEndY });
                        return pts;
                    }
                }
            }
        }

        return null;
    }

    /** Collapses runs of collinear points so only actual bends remain. */
    private List<double[]> simplify(List<double[]> pts) {
        if (pts.size() <= 2)
            return pts;
        List<double[]> out = new ArrayList<>();
        out.add(pts.get(0));
        for (int i = 1; i < pts.size() - 1; i++) {
            double[] prev = out.get(out.size() - 1);
            double[] curr = pts.get(i);
            double[] next = pts.get(i + 1);
            boolean collinear = (prev[0] == curr[0] && curr[0] == next[0]) ||
                    (prev[1] == curr[1] && curr[1] == next[1]);
            if (!collinear) {
                out.add(curr);
            }
        }
        out.add(pts.get(pts.size() - 1));
        return out;
    }
}
