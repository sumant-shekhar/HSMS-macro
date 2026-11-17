package com.example.blockminer.pathfinder;

public enum Moves {
    TRAVERSE_NORTH(0, -1, Type.TRAVERSE),
    TRAVERSE_SOUTH(0, 1, Type.TRAVERSE),
    TRAVERSE_EAST(1, 0, Type.TRAVERSE),
    TRAVERSE_WEST(-1, 0, Type.TRAVERSE),
    ASCEND_NORTH(0, -1, Type.ASCEND),
    ASCEND_SOUTH(0, 1, Type.ASCEND),
    ASCEND_EAST(1, 0, Type.ASCEND),
    ASCEND_WEST(-1, 0, Type.ASCEND),
    DESCEND_NORTH(0, -1, Type.DESCEND),
    DESCEND_SOUTH(0, 1, Type.DESCEND),
    DESCEND_EAST(1, 0, Type.DESCEND),
    DESCEND_WEST(-1, 0, Type.DESCEND),
    DIAGONAL_NORTHEAST(1, -1, Type.DIAGONAL),
    DIAGONAL_NORTHWEST(-1, -1, Type.DIAGONAL),
    DIAGONAL_SOUTHEAST(1, 1, Type.DIAGONAL),
    DIAGONAL_SOUTHWEST(-1, 1, Type.DIAGONAL);

    public enum Type {
        TRAVERSE,
        ASCEND,
        DESCEND,
        DIAGONAL
    }

    public final int offsetX;
    public final int offsetZ;
    public final Type type;

    Moves(int offsetX, int offsetZ, Type type) {
        this.offsetX = offsetX;
        this.offsetZ = offsetZ;
        this.type = type;
    }
}
