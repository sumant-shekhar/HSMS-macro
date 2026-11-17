package com.example.blockminer.pathfinder;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

public final class PathfindingSelfTest {
    private PathfindingSelfTest() {
    }

    public static void main(String[] args) {
        testHeapOrderingAndRelocation();
        testAStarOnSyntheticGrid();
        testStraightLineOnFlatGround();
        testAscendOneBlock();
        testDescendOneBlock();
        testUnreachableGoalReturnsFailure();
        System.out.println("Pathfinding self-tests passed (6 scenarios, 0 failures).");
    }

    private static void testHeapOrderingAndRelocation() {
        IGoal goal = zeroGoal();
        BinaryHeapOpenSet openSet = new BinaryHeapOpenSet(2);
        Random random = new Random(92731L);
        List<PathNode> inserted = new ArrayList<>();

        for (int i = 0; i < 250; i++) {
            PathNode node = new PathNode(i, 0, 0, goal);
            node.totalCost = random.nextDouble() * 1000;
            inserted.add(node);
            openSet.add(node);
        }

        PathNode decreased = inserted.get(inserted.size() - 1);
        decreased.totalCost = -1;
        openSet.relocate(decreased);
        require(openSet.poll() == decreased, "decreased node should relocate to the heap root");

        double previousCost = Double.NEGATIVE_INFINITY;
        while (!openSet.isEmpty()) {
            PathNode node = openSet.poll();
            require(node.totalCost >= previousCost, "heap poll order must be nondecreasing");
            require(node.heapPosition == -1, "polled nodes must no longer have a heap position");
            previousCost = node.totalCost;
        }
        require(openSet.size() == 0, "heap must be empty after polling all nodes");
    }

    private static void testAStarOnSyntheticGrid() {
        ActionCosts costs = new ActionCosts();
        Goal goal = new Goal(4, 0, 0, costs);
        MoveCalculator grid = (move, x, y, z, result) -> {
            int nextX = x + move.offsetX;
            int nextZ = z + move.offsetZ;
            if (move.type == Moves.Type.ASCEND || move.type == Moves.Type.DESCEND
                    || nextX < 0 || nextX > 4 || nextZ < 0 || nextZ > 2
                    || (nextX == 2 && nextZ == 0)) {
                return;
            }
            result.set(nextX, y, nextZ);
            result.cost = move.type == Moves.Type.DIAGONAL ? Math.sqrt(2.0) : 1.0;
        };

        PathResult result = new AStarPathFinder(0, 0, 0, goal, grid).calculatePath();
        require(result != null, "A* should find a route around the blocked grid cell");
        List<PathNode> path = result.getNodes();
        require(path.get(0).x == 0 && path.get(0).y == 0 && path.get(0).z == 0,
                "path must begin at the requested start");
        PathNode last = path.get(path.size() - 1);
        require(goal.isAtGoal(last.x, last.y, last.z), "path must end at the goal");
        require(path.stream().noneMatch(node -> node.x == 2 && node.z == 0),
                "path must not cross the blocked grid cell");
        require(path.size() > 4, "route must detour around the obstacle");
    }

    private static void testStraightLineOnFlatGround() {
        Goal goal = new Goal(4, 0, 0, new ActionCosts());
        MoveCalculator flatGround = (move, x, y, z, result) -> {
            if (move == Moves.TRAVERSE_EAST && y == 0 && z == 0 && x < 4) {
                result.set(x + 1, 0, 0);
                result.cost = 1.0;
            }
        };

        PathResult result = new AStarPathFinder(0, 0, 0, goal, flatGround).calculatePath();
        require(result != null, "A* should find a straight path on flat ground");
        require(result.getNodes().size() == 5, "straight path should contain start and four steps");
        require(result.getCost() == 4.0, "straight path cost should equal four unit moves");
        for (int i = 0; i < result.getNodes().size(); i++) {
            PathNode node = result.getNodes().get(i);
            require(node.x == i && node.y == 0 && node.z == 0,
                    "straight path should advance exactly one block east at each step");
        }
    }

    private static void testAscendOneBlock() {
        Goal goal = new Goal(1, 1, 0, new ActionCosts());
        MoveCalculator oneBlockStep = (move, x, y, z, result) -> {
            if (move == Moves.ASCEND_EAST && x == 0 && y == 0 && z == 0) {
                result.set(1, 1, 0);
                result.cost = 1.0;
            }
        };

        PathResult result = new AStarPathFinder(0, 0, 0, goal, oneBlockStep).calculatePath();
        require(result != null, "A* should find a one-block ascent");
        require(result.getNodes().size() == 2, "one-block ascent should contain start and goal");
        PathNode end = result.getNodes().get(1);
        require(end.x == 1 && end.y == 1 && end.z == 0, "ascent should end one block higher");
    }

    private static void testDescendOneBlock() {
        Goal goal = new Goal(1, -1, 0, new ActionCosts());
        MoveCalculator oneBlockStep = (move, x, y, z, result) -> {
            if (move == Moves.DESCEND_EAST && x == 0 && y == 0 && z == 0) {
                result.set(1, -1, 0);
                result.cost = 1.0;
            }
        };

        PathResult result = new AStarPathFinder(0, 0, 0, goal, oneBlockStep).calculatePath();
        require(result != null, "A* should find a one-block descent");
        require(result.getNodes().size() == 2, "one-block descent should contain start and goal");
        PathNode end = result.getNodes().get(1);
        require(end.x == 1 && end.y == -1 && end.z == 0, "descent should end one block lower");
    }

    private static void testUnreachableGoalReturnsFailure() {
        Goal goal = new Goal(100, 0, 0, new ActionCosts());
        AtomicReference<PathResult> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread search = new Thread(() -> {
            try {
                result.set(new AStarPathFinder(0, 0, 0, goal,
                        (move, x, y, z, movement) -> { }).calculatePath());
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        }, "unreachable-path-self-test");
        search.setDaemon(true);
        search.start();
        try {
            search.join(2_000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("unreachable-goal test was interrupted", exception);
        }

        require(!search.isAlive(), "unreachable search should terminate instead of looping");
        require(failure.get() == null, "unreachable search should not throw");
        require(result.get() == null, "unreachable search should return the failure result null");
    }

    private static IGoal zeroGoal() {
        return new IGoal() {
            @Override
            public boolean isAtGoal(int x, int y, int z) {
                return false;
            }

            @Override
            public double heuristic(int x, int y, int z) {
                return 0;
            }
        };
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
