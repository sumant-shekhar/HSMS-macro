package com.example.blockminer.pathfinder;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;

public final class PathfindingSnapshot {
    private static final int HORIZONTAL_PADDING = 16;
    private static final int VERTICAL_PADDING = 8;
    private static final long MAX_CELLS = 1_500_000L;

    private final int minX;
    private final int minY;
    private final int minZ;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final Cell[] cells;

    private PathfindingSnapshot(
            int minX,
            int minY,
            int minZ,
            int sizeX,
            int sizeY,
            int sizeZ,
            Cell[] cells
    ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.cells = cells.clone();
    }

    public static PathfindingSnapshot capture(
            ClientLevel level,
            BlockPos start,
            BlockPos goal
    ) {
        // This capture runs on the client thread; only primitive block facts escape to the search worker.
        if (start.getY() < level.getMinY() || start.getY() >= level.getMaxY()
                || goal.getY() < level.getMinY() || goal.getY() >= level.getMaxY()) {
            throw new IllegalArgumentException("Start and goal must be inside the world's build height.");
        }

        int minX = boundedCoordinate((long) Math.min(start.getX(), goal.getX()) - HORIZONTAL_PADDING);
        int maxX = boundedCoordinate((long) Math.max(start.getX(), goal.getX()) + HORIZONTAL_PADDING);
        int minZ = boundedCoordinate((long) Math.min(start.getZ(), goal.getZ()) - HORIZONTAL_PADDING);
        int maxZ = boundedCoordinate((long) Math.max(start.getZ(), goal.getZ()) + HORIZONTAL_PADDING);
        int minY = (int) Math.max(
                level.getMinY(),
                (long) Math.min(start.getY(), goal.getY()) - VERTICAL_PADDING
        );
        int maxY = (int) Math.min(
                (long) level.getMaxY() - 1L,
                (long) Math.max(start.getY(), goal.getY()) + VERTICAL_PADDING
        );
        int sizeX = checkedSize(minX, maxX);
        int sizeY = checkedSize(minY, maxY);
        int sizeZ = checkedSize(minZ, maxZ);
        long xAndY = (long) sizeX * sizeY;
        if (xAndY > MAX_CELLS || xAndY > MAX_CELLS / sizeZ) {
            long requested = xAndY > Long.MAX_VALUE / sizeZ ? Long.MAX_VALUE : xAndY * sizeZ;
            throw new IllegalArgumentException(
                    "Requested search region is too large (" + requested + " cells; limit " + MAX_CELLS + ")."
            );
        }
        long cellCount = xAndY * sizeZ;

        Cell[] cells = new Cell[(int) cellCount];
        Map<Long, LevelChunkSection[]> loadedSections = copyLoadedChunkSections(level, minX, maxX, minZ, maxZ);
        for (int y = minY; y <= maxY; y++) {
            int sectionIndex = level.getSectionIndex(y);
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    LevelChunkSection[] sections = loadedSections.get(
                            chunkKey(Math.floorDiv(x, 16), Math.floorDiv(z, 16))
                    );
                    if (sections == null) {
                        cells[index(x, y, z, minX, minY, minZ, sizeX, sizeZ)] = Cell.unavailable();
                        continue;
                    }

                    if (sectionIndex < 0 || sectionIndex >= sections.length || sections[sectionIndex] == null) {
                        cells[index(x, y, z, minX, minY, minZ, sizeX, sizeZ)] = Cell.unavailable();
                        continue;
                    }

                    BlockState state = sections[sectionIndex].getBlockState(x & 15, y & 15, z & 15);
                    BlockPos position = new BlockPos(x, y, z);
                    VoxelShape shape = state.getCollisionShape(level, position, CollisionContext.empty());
                    FluidState fluid = state.getFluidState();
                    boolean liquid = !fluid.isEmpty();
                    boolean water = fluid.is(FluidTags.WATER);
                    boolean lava = fluid.is(FluidTags.LAVA);
                    boolean slab = state.is(BlockTags.SLABS);
                    boolean stair = state.is(BlockTags.STAIRS);
                    boolean carpet = state.is(BlockTags.WOOL_CARPETS);
                    boolean door = state.is(BlockTags.DOORS);
                    boolean openDoor = door && state.hasProperty(BlockStateProperties.OPEN)
                            && state.getValue(BlockStateProperties.OPEN);
                    boolean bottomHalf = !stair || !state.hasProperty(BlockStateProperties.HALF)
                            || state.getValue(BlockStateProperties.HALF) == Half.BOTTOM;
                    boolean bottomSlab = slab && state.hasProperty(BlockStateProperties.SLAB_TYPE)
                            && state.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.BOTTOM;
                    Direction facing = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                            ? state.getValue(BlockStateProperties.HORIZONTAL_FACING)
                            : Direction.UP;
                    boolean hazardous = state.getBlock() == Blocks.FIRE
                            || state.getBlock() == Blocks.SOUL_FIRE
                            || state.getBlock() == Blocks.COBWEB
                            || state.getBlock() == Blocks.END_PORTAL
                            || state.getBlock() == Blocks.TRIPWIRE
                            || state.getBlock() == Blocks.COCOA
                            || state.getBlock() == Blocks.CACTUS
                            || state.getBlock() instanceof SkullBlock
                            || state.getBlock() instanceof AbstractCauldronBlock
                            || state.is(BlockTags.TRAPDOORS);

                    cells[index(x, y, z, minX, minY, minZ, sizeX, sizeZ)] = new Cell(
                            true,
                            state.isCollisionShapeFullBlock(level, position),
                            shape.isEmpty(),
                            liquid,
                            water,
                            lava,
                            liquid && !fluid.isSource(),
                            state.is(BlockTags.CLIMBABLE),
                            slab,
                            bottomSlab,
                            stair,
                            bottomHalf,
                            carpet,
                            door,
                            openDoor,
                            hazardous,
                            shape.isEmpty() ? 0.0 : shape.max(Direction.Axis.Y),
                            horizontalFacing(facing)
                    );
                }
            }
        }

        return new PathfindingSnapshot(minX, minY, minZ, sizeX, sizeY, sizeZ, cells);
    }

    public static PathfindingSnapshot fromCells(
            int minX,
            int minY,
            int minZ,
            int sizeX,
            int sizeY,
            int sizeZ,
            Cell[] cells
    ) {
        long xAndY = (long) sizeX * sizeY;
        if (cells == null || sizeX <= 0 || sizeY <= 0 || sizeZ <= 0
                || xAndY > Integer.MAX_VALUE || xAndY * sizeZ != cells.length) {
            throw new IllegalArgumentException("Snapshot dimensions must match the supplied cell array.");
        }
        return new PathfindingSnapshot(minX, minY, minZ, sizeX, sizeY, sizeZ, cells);
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x < (long) minX + sizeX
                && y >= minY && y < (long) minY + sizeY
                && z >= minZ && z < (long) minZ + sizeZ;
    }

    public Cell cellAt(int x, int y, int z) {
        if (!contains(x, y, z)) {
            return Cell.unavailable();
        }
        return cells[index(x, y, z, minX, minY, minZ, sizeX, sizeZ)];
    }

    private static Map<Long, LevelChunkSection[]> copyLoadedChunkSections(
            ClientLevel level,
            int minX,
            int maxX,
            int minZ,
            int maxZ
    ) {
        Map<Long, LevelChunkSection[]> chunks = new HashMap<>();
        int minChunkX = Math.floorDiv(minX, 16);
        int maxChunkX = Math.floorDiv(maxX, 16);
        int minChunkZ = Math.floorDiv(minZ, 16);
        int maxChunkZ = Math.floorDiv(maxZ, 16);
        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                LevelChunk chunk = level.getChunkSource().getChunk(
                        chunkX,
                        chunkZ,
                        ChunkStatus.FULL,
                        false
                );
                if (chunk != null) {
                    chunks.put(chunkKey(chunkX, chunkZ), chunk.getSections());
                }
            }
        }
        return chunks;
    }

    private static int checkedSize(int min, int max) {
        long size = (long) max - min + 1L;
        if (size <= 0L || size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Requested search region is outside supported coordinate limits.");
        }
        return (int) size;
    }

    private static int boundedCoordinate(long coordinate) {
        if (coordinate < Integer.MIN_VALUE || coordinate > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Requested search region is outside supported coordinate limits.");
        }
        return (int) coordinate;
    }

    private static int index(
            int x,
            int y,
            int z,
            int minX,
            int minY,
            int minZ,
            int sizeX,
            int sizeZ
    ) {
        return ((y - minY) * sizeZ + (z - minZ)) * sizeX + (x - minX);
    }

    private static long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFF_FFFFL);
    }

    private static int horizontalFacing(Direction direction) {
        return switch (direction) {
            case NORTH -> 0;
            case SOUTH -> 1;
            case EAST -> 2;
            case WEST -> 3;
            default -> -1;
        };
    }

    public record Cell(
            boolean loaded,
            boolean solid,
            boolean collisionEmpty,
            boolean liquid,
            boolean water,
            boolean lava,
            boolean flowing,
            boolean climbable,
            boolean slab,
            boolean bottomSlab,
            boolean stair,
            boolean bottomStair,
            boolean carpet,
            boolean door,
            boolean openDoor,
            boolean hazardous,
            double collisionTop,
            int facing
    ) {
        public static Cell unavailable() {
            return new Cell(false, false, false, false, false, false, false, false, false, false,
                    false, false, false, false, false, true, 0.0, -1);
        }

        public static Cell air() {
            return new Cell(true, false, true, false, false, false, false, false, false, false,
                    false, false, false, false, false, false, 0.0, -1);
        }

        public static Cell solidBlock(double collisionTop) {
            return new Cell(true, true, false, false, false, false, false, false, false, false,
                    false, false, false, false, false, false, collisionTop, -1);
        }

        public static Cell carpet(double collisionTop) {
            return new Cell(true, false, false, false, false, false, false, false, false, false,
                    false, false, true, false, false, false, collisionTop, -1);
        }
    }
}
