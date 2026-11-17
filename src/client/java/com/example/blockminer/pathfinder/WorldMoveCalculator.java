package com.example.blockminer.pathfinder;

public final class WorldMoveCalculator implements MoveCalculator {
    private static final double SQRT_2 = Math.sqrt(2.0);
    private final PathfindingSnapshot snapshot;
    private final ActionCosts costs;
    private final int maxFallHeight;

    public WorldMoveCalculator(PathfindingSnapshot snapshot, ActionCosts costs) {
        this(snapshot, costs, 20);
    }

    WorldMoveCalculator(PathfindingSnapshot snapshot, ActionCosts costs, int maxFallHeight) {
        this.snapshot = snapshot;
        this.costs = costs;
        this.maxFallHeight = maxFallHeight;
    }

    @Override
    public void calculate(Moves move, int x, int y, int z, MovementResult result) {
        int destX = x + move.offsetX;
        int destZ = z + move.offsetZ;
        switch (move.type) {
            case TRAVERSE -> evaluateStep(move, x, y, z, destX, y, destZ, false, result);
            case ASCEND -> evaluateStep(move, x, y, z, destX, y + 1, destZ, false, result);
            case DESCEND -> evaluateDescent(move, x, y, z, destX, destZ, false, result);
            case DIAGONAL -> calculateDiagonal(move, x, y, z, destX, destZ, result);
        }
    }

    private void calculateDiagonal(Moves move, int x, int y, int z, int destX, int destZ, MovementResult result) {
        if (!MovementHelper.canWalkThrough(snapshot, x + move.offsetX, y + 1, z)
                || !MovementHelper.canWalkThrough(snapshot, x + move.offsetX, y + 2, z)
                || !MovementHelper.canWalkThrough(snapshot, x, y + 1, z + move.offsetZ)
                || !MovementHelper.canWalkThrough(snapshot, x, y + 2, z + move.offsetZ)) {
            return;
        }
        if (evaluateStep(move, x, y, z, destX, y, destZ, true, result)) {
            return;
        }
        result.reset();
        if (evaluateStep(move, x, y, z, destX, y + 1, destZ, true, result)) {
            return;
        }
        result.reset();
        evaluateDescent(move, x, y, z, destX, destZ, true, result);
    }

    private boolean evaluateStep(
            Moves move,
            int x,
            int y,
            int z,
            int destX,
            int destY,
            int destZ,
            boolean diagonal,
            MovementResult result
    ) {
        PathfindingSnapshot.Cell source = snapshot.cellAt(x, y, z);
        PathfindingSnapshot.Cell destination = snapshot.cellAt(destX, destY, destZ);
        if (!MovementHelper.canStandOn(snapshot, x, y, z, source)
                || !MovementHelper.canStandOn(snapshot, destX, destY, destZ, destination)
                || !MovementHelper.canWalkThrough(snapshot, destX, destY + 1, destZ)
                || !MovementHelper.canWalkThrough(snapshot, destX, destY + 2, destZ)) {
            return false;
        }
        if (move.type == Moves.Type.ASCEND
                && (!MovementHelper.canWalkThrough(snapshot, x, y + 3, z)
                || MovementHelper.isClimbable(snapshot, x, y, z))) {
            return false;
        }

        PathfindingSnapshot.Cell sourceBody = snapshot.cellAt(x, y + 1, z);
        if (sourceBody.lava()) {
            return false;
        }
        double sourceTop = y + MovementHelper.collisionTop(source);
        double destinationTop = destY + MovementHelper.collisionTop(destination);
        double heightDifference = destinationTop - sourceTop;
        double cost;
        if (sourceBody.water()) {
            cost = costs.ONE_BLOCK_WALK_IN_WATER_COST;
        } else if (heightDifference <= 0.5) {
            cost = costs.ONE_BLOCK_SPRINT_COST;
        } else if (heightDifference <= (move.type == Moves.Type.ASCEND ? 1.125 : 1.0)) {
            cost = costs.JUMP_ONE_BLOCK_COST;
        } else {
            return false;
        }

        result.set(destX, destY, destZ);
        result.cost = diagonal ? cost * SQRT_2 : cost;
        return true;
    }

    private void evaluateDescent(
            Moves move,
            int x,
            int y,
            int z,
            int destX,
            int destZ,
            boolean diagonal,
            MovementResult result
    ) {
        PathfindingSnapshot.Cell source = snapshot.cellAt(x, y, z);
        if (!source.loaded() || !MovementHelper.canStandOn(snapshot, x, y, z, source)
                || MovementHelper.isClimbable(snapshot, x, y, z)) {
            return;
        }

        for (int destY = y - 1; destY >= y - maxFallHeight; destY--) {
            if (!MovementHelper.canWalkThrough(snapshot, destX, destY + 1, destZ)
                    || !MovementHelper.canWalkThrough(snapshot, destX, destY + 2, destZ)) {
                return;
            }
            PathfindingSnapshot.Cell destination = snapshot.cellAt(destX, destY, destZ);
            if (!destination.loaded()) {
                return;
            }
            if (!MovementHelper.canStandOn(snapshot, destX, destY, destZ, destination)) {
                if (!MovementHelper.canWalkThrough(snapshot, destX, destY, destZ)) {
                    return;
                }
                continue;
            }

            double sourceTop = y + MovementHelper.collisionTop(source);
            double destinationTop = destY + MovementHelper.collisionTop(destination);
            double fallDistance = Math.max(0.0, sourceTop - destinationTop);
            int fallBlocks = (int) Math.ceil(fallDistance);
            if (fallBlocks > maxFallHeight || fallBlocks >= costs.N_BLOCK_FALL_COST.length) {
                return;
            }
            double cost = fallDistance <= 0.5
                    ? costs.ONE_BLOCK_WALK_COST
                    : costs.WALK_OFF_ONE_BLOCK_COST * costs.SPRINT_MULTIPLIER
                    + costs.N_BLOCK_FALL_COST[fallBlocks];
            result.set(destX, destY, destZ);
            result.cost = diagonal ? cost * SQRT_2 : cost;
            return;
        }
    }
}
