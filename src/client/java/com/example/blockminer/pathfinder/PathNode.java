package com.example.blockminer.pathfinder;

import net.minecraft.core.BlockPos;

public final class PathNode {
    public final int x;
    public final int y;
    public final int z;
    public double costSoFar = ActionCosts.INF_COST;
    public final double costToEnd;
    public double totalCost = ActionCosts.INF_COST;
    public int heapPosition = -1;
    public PathNode parentNode;

    public PathNode(int x, int y, int z, IGoal goal) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.costToEnd = goal.heuristic(x, y, z);
    }

    public BlockPos getBlock() {
        return new BlockPos(x, y, z);
    }

    public static long longHash(int x, int y, int z) {
        long hash = 3241L;
        hash = 3457689L * hash + x;
        hash = 8734625L * hash + y;
        hash = 2873465L * hash + z;
        return hash;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PathNode node)) {
            return false;
        }
        return x == node.x && y == node.y && z == node.z;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(longHash(x, y, z));
    }

    @Override
    public String toString() {
        return "PathNode(x: " + x + ", y: " + y + ", z: " + z
                + ", costSoFar: " + costSoFar + ", costToEnd: " + costToEnd
                + ", totalCost: " + totalCost + ")";
    }
}
