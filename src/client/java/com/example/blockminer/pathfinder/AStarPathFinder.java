package com.example.blockminer.pathfinder;

import java.util.HashMap;
import java.util.Map;

public final class AStarPathFinder {
    private final int startX;
    private final int startY;
    private final int startZ;
    private final IGoal goal;
    private final MoveCalculator moveCalculator;
    private final Map<PositionKey, PathNode> nodes = new HashMap<>();
    private volatile boolean calculating;

    public AStarPathFinder(int startX, int startY, int startZ, IGoal goal, MoveCalculator moveCalculator) {
        this.startX = startX;
        this.startY = startY;
        this.startZ = startZ;
        this.goal = goal;
        this.moveCalculator = moveCalculator;
    }

    public synchronized PathResult calculatePath() {
        if (calculating) {
            throw new IllegalStateException("pathfinder is already calculating");
        }
        nodes.clear();
        calculating = true;
        try {
            BinaryHeapOpenSet openSet = new BinaryHeapOpenSet();
            PathNode startNode = getNode(startX, startY, startZ);
            MovementResult result = new MovementResult();
            startNode.costSoFar = 0.0;
            startNode.totalCost = startNode.costToEnd;
            openSet.add(startNode);

            while (!openSet.isEmpty() && calculating) {
                PathNode currentNode = openSet.poll();
                if (goal.isAtGoal(currentNode.x, currentNode.y, currentNode.z)) {
                    return new PathResult(currentNode);
                }

                for (Moves move : Moves.values()) {
                    result.reset();
                    moveCalculator.calculate(move, currentNode.x, currentNode.y, currentNode.z, result);
                    if (!(result.cost < ActionCosts.INF_COST)) {
                        continue;
                    }

                    PathNode neighbour = getNode(result.x, result.y, result.z);
                    double candidateCost = currentNode.costSoFar + result.cost;
                    if (neighbour.costSoFar > candidateCost) {
                        neighbour.parentNode = currentNode;
                        neighbour.costSoFar = candidateCost;
                        neighbour.totalCost = candidateCost + neighbour.costToEnd;
                        if (neighbour.heapPosition == -1) {
                            openSet.add(neighbour);
                        } else {
                            openSet.relocate(neighbour);
                        }
                    }
                }
            }
            return null;
        } finally {
            calculating = false;
        }
    }

    public PathNode getNode(int x, int y, int z) {
        return nodes.computeIfAbsent(new PositionKey(x, y, z), ignored -> new PathNode(x, y, z, goal));
    }

    public void requestStop() {
        calculating = false;
    }

    private record PositionKey(int x, int y, int z) {
    }
}
