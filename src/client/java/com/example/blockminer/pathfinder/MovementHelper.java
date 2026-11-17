package com.example.blockminer.pathfinder;

public final class MovementHelper {
    private MovementHelper() {
    }

    public static boolean canWalkThrough(PathfindingSnapshot snapshot, int x, int y, int z) {
        return canWalkThrough(snapshot, x, y, z, snapshot.cellAt(x, y, z));
    }

    public static boolean canWalkThrough(
            PathfindingSnapshot snapshot,
            int x,
            int y,
            int z,
            PathfindingSnapshot.Cell cell
    ) {
        if (!cell.loaded() || cell.hazardous() || cell.lava() || cell.climbable()) {
            return false;
        }
        if (cell.door()) {
            return cell.openDoor();
        }
        if (cell.carpet()) {
            return canStandOn(snapshot, x, y - 1, z);
        }
        if (cell.liquid()) {
            if (!cell.water() || cell.flowing() || !cell.collisionEmpty()) {
                return false;
            }
            PathfindingSnapshot.Cell above = snapshot.cellAt(x, y + 1, z);
            return above.loaded() && !above.liquid() && above.collisionEmpty();
        }
        return cell.collisionEmpty();
    }

    public static boolean canStandOn(PathfindingSnapshot snapshot, int x, int y, int z) {
        return canStandOn(snapshot, x, y, z, snapshot.cellAt(x, y, z));
    }

    public static boolean canStandOn(
            PathfindingSnapshot snapshot,
            int x,
            int y,
            int z,
            PathfindingSnapshot.Cell cell
    ) {
        if (!cell.loaded() || cell.lava() || cell.hazardous() || cell.door() || cell.carpet()) {
            return false;
        }
        if (cell.liquid() && cell.collisionTop() <= 0.0) {
            return false;
        }
        return cell.climbable() || cell.solid() || cell.slab() || cell.stair()
                || (cell.collisionTop() > 0.0 && cell.collisionTop() <= 1.0);
    }

    public static boolean isSolid(PathfindingSnapshot snapshot, int x, int y, int z) {
        PathfindingSnapshot.Cell cell = snapshot.cellAt(x, y, z);
        return cell.loaded() && cell.solid();
    }

    public static boolean isPassable(PathfindingSnapshot snapshot, int x, int y, int z) {
        return canWalkThrough(snapshot, x, y, z);
    }

    public static boolean isLiquid(PathfindingSnapshot snapshot, int x, int y, int z) {
        PathfindingSnapshot.Cell cell = snapshot.cellAt(x, y, z);
        return cell.loaded() && cell.liquid();
    }

    public static boolean isClimbable(PathfindingSnapshot snapshot, int x, int y, int z) {
        PathfindingSnapshot.Cell cell = snapshot.cellAt(x, y, z);
        return cell.loaded() && cell.climbable();
    }

    public static boolean isBottomSlab(PathfindingSnapshot.Cell cell) {
        return cell.slab() && cell.bottomSlab();
    }

    public static boolean isStair(PathfindingSnapshot.Cell cell) {
        return cell.stair() && cell.bottomStair();
    }

    public static boolean canWalkIntoClimbable(PathfindingSnapshot.Cell cell, int dx, int dz) {
        if (!cell.climbable()) {
            return false;
        }
        int facing = dx == 0 && dz == -1 ? 0
                : dx == 0 && dz == 1 ? 1
                : dx == 1 && dz == 0 ? 2
                : dx == -1 && dz == 0 ? 3
                : -1;
        return facing >= 0 && cell.facing() != facing;
    }

    public static double collisionTop(PathfindingSnapshot.Cell cell) {
        return cell.loaded() ? cell.collisionTop() : 0.0;
    }
}
