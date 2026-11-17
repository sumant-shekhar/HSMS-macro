package com.example.blockminer.pathfinder;

import net.minecraft.core.BlockPos;

public final class MovementResult {
    public int x;
    public int y;
    public int z;
    public double cost = ActionCosts.INF_COST;

    public void set(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void reset() {
        x = 0;
        y = 0;
        z = 0;
        cost = ActionCosts.INF_COST;
    }

    public BlockPos getDest() {
        return new BlockPos(x, y, z);
    }
}
