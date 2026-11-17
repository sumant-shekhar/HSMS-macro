package com.example.blockminer.pathfinder;

import java.util.Arrays;

public final class PathfindingAdapterSelfTest {
    private PathfindingAdapterSelfTest() {
    }

    public static void main(String[] args) {
        testImmutableSnapshotBounds();
        testWalkabilityFacts();
        testFlatPathUsesWorldMovementCosts();
        testWaterMovementCost();
        testOneBlockAscent();
        testFallFindsSupportedLanding();
        System.out.println("Pathfinding adapter self-tests passed (6 scenarios, 0 failures).");
    }

    private static void testImmutableSnapshotBounds() {
        PathfindingSnapshot.Cell[] cells = {
                PathfindingSnapshot.Cell.solidBlock(1.0),
                PathfindingSnapshot.Cell.air()
        };
        PathfindingSnapshot snapshot = PathfindingSnapshot.fromCells(-2, 4, 8, 2, 1, 1, cells);
        cells[0] = PathfindingSnapshot.Cell.air();

        require(snapshot.contains(-2, 4, 8), "snapshot must include its minimum corner");
        require(snapshot.cellAt(-2, 4, 8).solid(), "snapshot must defensively copy its cells");
        require(!snapshot.contains(0, 4, 8), "snapshot must exclude coordinates past its bounds");
        require(!snapshot.cellAt(0, 4, 8).loaded(), "out-of-bounds cells must be unavailable, not air");
    }

    private static void testWalkabilityFacts() {
        PathfindingSnapshot.Cell[] cells = {
                PathfindingSnapshot.Cell.solidBlock(1.0),
                PathfindingSnapshot.Cell.carpet(0.0625),
                PathfindingSnapshot.Cell.air()
        };
        PathfindingSnapshot snapshot = PathfindingSnapshot.fromCells(0, 0, 0, 1, 3, 1, cells);

        require(MovementHelper.isSolid(snapshot, 0, 0, 0), "solid query must use copied state facts");
        require(MovementHelper.canWalkThrough(snapshot, 0, 1, 0),
                "carpet should be passable when supported by a standable block");
        require(MovementHelper.isPassable(snapshot, 0, 2, 0), "air should be passable");
        require(!MovementHelper.isPassable(snapshot, 2, 2, 0), "unloaded coordinates must be blocked");

        PathfindingSnapshot.Cell bottomSlab = new PathfindingSnapshot.Cell(
                true, false, false, false, false, false, false, false, true, true,
                false, false, false, false, false, false, 0.5, -1
        );
        PathfindingSnapshot.Cell bottomStair = new PathfindingSnapshot.Cell(
                true, false, false, false, false, false, false, false, false, false,
                true, true, false, false, false, false, 1.0, 0
        );
        PathfindingSnapshot.Cell climbable = new PathfindingSnapshot.Cell(
                true, false, false, false, false, false, false, true, false, false,
                false, false, false, false, false, false, 1.0, 0
        );
        require(MovementHelper.isBottomSlab(bottomSlab), "slab tag/property facts should identify a bottom slab");
        require(MovementHelper.isStair(bottomStair), "stair tag/half facts should identify a bottom stair");
        require(MovementHelper.canStandOn(snapshot, 0, 0, 0, climbable),
                "climbable blocks should be considered standable support");
        require(MovementHelper.canWalkIntoClimbable(climbable, 0, 1),
                "ladder facing facts should permit entry from the open side");

        PathfindingSnapshot.Cell water = new PathfindingSnapshot.Cell(
                true, false, true, true, true, false, false, false, false, false,
                false, false, false, false, false, false, 0.0, -1
        );
        PathfindingSnapshot waterSnapshot = PathfindingSnapshot.fromCells(
                0, 0, 0, 1, 2, 1,
                new PathfindingSnapshot.Cell[]{water, PathfindingSnapshot.Cell.air()}
        );
        require(MovementHelper.isLiquid(waterSnapshot, 0, 0, 0), "fluid query must identify water");
        require(MovementHelper.canWalkThrough(waterSnapshot, 0, 0, 0),
                "still source water with empty space above should be traversable");

        PathfindingSnapshot.Cell flowingWater = new PathfindingSnapshot.Cell(
                true, false, true, true, true, false, true, false, false, false,
                false, false, false, false, false, false, 0.0, -1
        );
        PathfindingSnapshot flowingSnapshot = PathfindingSnapshot.fromCells(
                0, 0, 0, 1, 2, 1,
                new PathfindingSnapshot.Cell[]{flowingWater, PathfindingSnapshot.Cell.air()}
        );
        require(!MovementHelper.canWalkThrough(flowingSnapshot, 0, 0, 0),
                "flowing water should be blocked");
    }

    private static void testFlatPathUsesWorldMovementCosts() {
        TestGrid grid = flatGrid(0, -1, 4, 3, -1, 1, 0);
        PathResult result = path(grid.snapshot(), new ActionCosts(), 0, 0, 0, 4, 0, 0);

        require(result != null, "world movement costs should find a flat path");
        require(result.getNodes().size() == 5, "flat path should contain four steps plus its start");
        require(result.getCost() > 0.0, "world movement costs should produce a positive route cost");
    }

    private static void testOneBlockAscent() {
        TestGrid grid = flatGrid(0, -1, 2, 3, -1, 1, 0);
        grid.set(1, 1, 0, PathfindingSnapshot.Cell.solidBlock(1.0));
        PathResult result = path(grid.snapshot(), new ActionCosts(), 0, 0, 0, 1, 1, 0);

        require(result != null, "movement costs should find a one-block ascent");
        require(result.getNodes().size() == 2, "one-block ascent should be a single movement");
        require(result.getNodes().getLast().y == 1, "ascent should end at the higher support block");
    }

    private static void testWaterMovementCost() {
        TestGrid grid = flatGrid(0, -1, 1, 3, 0, 0, 0);
        PathfindingSnapshot.Cell water = new PathfindingSnapshot.Cell(
                true, false, true, true, true, false, false, false, false, false,
                false, false, false, false, false, false, 0.0, -1
        );
        grid.set(0, 1, 0, water);
        grid.set(1, 1, 0, water);
        ActionCosts costs = new ActionCosts();
        MovementResult result = new MovementResult();
        new WorldMoveCalculator(grid.snapshot(), costs).calculate(Moves.TRAVERSE_EAST, 0, 0, 0, result);

        require(result.cost == costs.ONE_BLOCK_WALK_IN_WATER_COST,
                "movement through source water should use the existing water cost");
    }

    private static void testFallFindsSupportedLanding() {
        TestGrid grid = flatGrid(0, -1, 2, 4, -1, 1, 0);
        grid.set(0, 2, 0, PathfindingSnapshot.Cell.solidBlock(1.0));
        grid.set(1, 1, 0, PathfindingSnapshot.Cell.solidBlock(1.0));
        ActionCosts costs = new ActionCosts();
        PathResult result = path(grid.snapshot(), costs, 0, 2, 0, 1, 1, 0);

        require(result != null, "descent should find a supported landing");
        require(result.getNodes().getLast().y == 1, "fall should stop at the destination support block");
        require(result.getCost() >= costs.N_BLOCK_FALL_COST[1],
                "fall route cost must include a fall-distance penalty");
    }

    private static PathResult path(
            PathfindingSnapshot snapshot,
            ActionCosts costs,
            int startX,
            int startY,
            int startZ,
            int goalX,
            int goalY,
            int goalZ
    ) {
        return new AStarPathFinder(
                startX,
                startY,
                startZ,
                new Goal(goalX, goalY, goalZ, costs),
                new WorldMoveCalculator(snapshot, costs)
        ).calculatePath();
    }

    private static TestGrid flatGrid(
            int minX,
            int minY,
            int maxX,
            int maxY,
            int minZ,
            int maxZ,
            int floorY
    ) {
        TestGrid grid = new TestGrid(minX, minY, minZ, maxX, maxY, maxZ);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                grid.set(x, floorY, z, PathfindingSnapshot.Cell.solidBlock(1.0));
            }
        }
        return grid;
    }

    private static final class TestGrid {
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;
        private final int sizeX;
        private final int sizeZ;
        private final PathfindingSnapshot.Cell[] cells;

        private TestGrid(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            sizeX = maxX - minX + 1;
            sizeZ = maxZ - minZ + 1;
            cells = new PathfindingSnapshot.Cell[sizeX * (maxY - minY + 1) * sizeZ];
            Arrays.fill(cells, PathfindingSnapshot.Cell.air());
        }

        private void set(int x, int y, int z, PathfindingSnapshot.Cell cell) {
            if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) {
                throw new IllegalArgumentException("Test cell is outside the grid.");
            }
            int index = ((y - minY) * sizeZ + (z - minZ)) * sizeX + (x - minX);
            cells[index] = cell;
        }

        private PathfindingSnapshot snapshot() {
            return PathfindingSnapshot.fromCells(
                    minX,
                    minY,
                    minZ,
                    sizeX,
                    maxY - minY + 1,
                    sizeZ,
                    cells
            );
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
