package com.example.blockminer;

import com.example.blockminer.heap.MinHeap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BlockUtil {
	private static final int HORIZONTAL_SCAN_RADIUS = 5;
	private static final int VERTICAL_SCAN_BELOW = -3;
	private static final int VERTICAL_SCAN_ABOVE = 4;
	private static final double MAX_REACH = 4.0D;

	private BlockUtil() {
	}

	public static List<BlockCandidate> findMineableBlocksFromAccessiblePositions(
			Map<Block, Integer> priorities,
			BlockPos blockToIgnore,
			int miningSpeed
	) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		ClientLevel level = client.level;
		if (player == null || level == null) {
			return List.of();
		}
		if (miningSpeed <= 0) {
			throw new IllegalArgumentException("Mining speed must be positive");
		}

		BlockPos currentFloor = PlayerUtil.getBlockStandingOn(player);
		BlockState currentSupport = level.getBlockState(currentFloor);
		boolean scanOneBlockLower = !currentSupport.getCollisionShape(level, currentFloor).isEmpty()
				&& currentSupport.getCollisionShape(level, currentFloor).max(Direction.Axis.Y) < 0.99D;
		Set<BlockPos> alreadyChecked = new HashSet<>();
		if (blockToIgnore != null) {
			alreadyChecked.add(blockToIgnore.immutable());
		}

		MinHeap<BlockCandidate> candidates = new MinHeap<>(256);
		for (int yOffset = scanOneBlockLower ? -1 : 0; yOffset <= 0; yOffset++) {
			for (int xOffset = -1; xOffset <= 1; xOffset++) {
				for (int zOffset = -1; zOffset <= 1; zOffset++) {
					BlockPos floor = currentFloor.offset(xOffset, yOffset, zOffset);
					if (!canStandAt(level, floor)) {
						continue;
					}
					double surfaceDelta = floor.getY()
							+ level.getBlockState(floor).getCollisionShape(level, floor).max(Direction.Axis.Y)
							- player.getY();
					Vec3 eye = player.getEyePosition().add(xOffset, surfaceDelta, zOffset);
					findMineableBlocksAroundPoint(level, player, eye, priorities, alreadyChecked, miningSpeed, candidates);
				}
			}
		}

		List<BlockCandidate> sorted = new ArrayList<>(candidates.size());
		while (!candidates.isEmpty()) {
			sorted.add(candidates.poll());
		}
		return List.copyOf(sorted);
	}

	public static List<BlockCandidate> findMineableBlocksAroundPlayer(
			Map<Block, Integer> priorities,
			int miningSpeed
	) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) {
			return List.of();
		}
		return findMineableBlocksFromAccessiblePositions(priorities, null, miningSpeed);
	}

	public static double score(
			double hardness,
			int miningSpeed,
			double angleChange,
			double distanceSquared,
			int priority
	) {
		if (miningSpeed <= 0 || priority <= 0
				|| !Double.isFinite(hardness) || !Double.isFinite(angleChange)
				|| !Double.isFinite(distanceSquared) || hardness < 0.0D
				|| angleChange < 0.0D || distanceSquared < 0.0D) {
			throw new IllegalArgumentException("Invalid block-selection scoring input");
		}
		return (hardness / miningSpeed * Config.miningCoefficient
				+ angleChange * Config.angleCoefficient
				+ distanceSquared * Config.distanceCoefficient) / priority;
	}

	public static double getBlockStrength(BlockState state, ClientLevel level, BlockPos position) {
		return state.getDestroySpeed(level, position);
	}

	public static int getMiningTime(BlockState state, ClientLevel level, BlockPos position, int miningSpeed) {
		if (miningSpeed <= 0) {
			throw new IllegalArgumentException("Mining speed must be positive");
		}
		double hardness = getBlockStrength(state, level, position);
		if (!Double.isFinite(hardness) || hardness < 0.0D) {
			return Integer.MAX_VALUE;
		}
		double ticks = Math.ceil(hardness * 30.0D / miningSpeed) + Config.tickGlideOffset;
		return (int) Math.max(1.0D, Math.min(Integer.MAX_VALUE, ticks));
	}

	public static boolean canStandAt(ClientLevel level, BlockPos floor) {
		BlockState support = level.getBlockState(floor);
		if (support.isAir() || !support.getFluidState().isEmpty()) {
			return false;
		}
		VoxelShape supportShape = support.getCollisionShape(level, floor);
		if (supportShape.isEmpty()
				|| supportShape.max(Direction.Axis.Y) <= 0.0D
				|| supportShape.max(Direction.Axis.X) - supportShape.min(Direction.Axis.X) < 0.5D
				|| supportShape.max(Direction.Axis.Z) - supportShape.min(Direction.Axis.Z) < 0.5D) {
			return false;
		}

		BlockPos feet = floor.above();
		return isSpaceClear(level, feet) && isSpaceClear(level, feet.above());
	}

	public static boolean hasVisibleSide(BlockPos position) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) {
			return false;
		}
		return !bestPointsOnBestSide(client.level, client.player, position).isEmpty();
	}

	public static List<Vec3> bestPointsOnBestSide(BlockPos position) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) {
			return List.of();
		}
		return bestPointsOnBestSide(client.level, client.player, position);
	}

	public static List<Vec3> bestPointsOnBestSide(ClientLevel level, LocalPlayer player, BlockPos position) {
		List<Vec3> points = new ArrayList<>();
		for (Direction side : Direction.values()) {
			for (int first = 0; first < 4; first++) {
				for (int second = 0; second < 4; second++) {
					double a = 0.16D + first * 0.226D;
					double b = 0.16D + second * 0.226D;
					Vec3 point = pointOnSide(position, side, a, b);
					if (RaytracingUtil.canSeePoint(level, player, player.getEyePosition(), point)) {
						points.add(point);
					}
				}
			}
		}

		Angle start = AngleUtil.getPlayerAngle();
		points.sort(Comparator.comparingDouble(point ->
				AngleUtil.getNeededChange(start, AngleUtil.getRotation(player.getEyePosition(), point)).lengthSqrt()));
		return List.copyOf(points);
	}

	private static void findMineableBlocksAroundPoint(
			ClientLevel level,
			LocalPlayer player,
			Vec3 eye,
			Map<Block, Integer> priorities,
			Set<BlockPos> alreadyChecked,
			int miningSpeed,
			MinHeap<BlockCandidate> candidates
	) {
		BlockPos origin = BlockPos.containing(eye);
		for (int y = VERTICAL_SCAN_BELOW; y <= VERTICAL_SCAN_ABOVE; y++) {
			for (int x = -HORIZONTAL_SCAN_RADIUS; x <= HORIZONTAL_SCAN_RADIUS; x++) {
				for (int z = -HORIZONTAL_SCAN_RADIUS; z <= HORIZONTAL_SCAN_RADIUS; z++) {
					BlockPos position = origin.offset(x, y, z);
					Vec3 center = Vec3.atCenterOf(position);
					double distanceSquared = eye.distanceToSqr(center);
					if (distanceSquared > MAX_REACH * MAX_REACH) {
						continue;
					}
					if (!alreadyChecked.add(position.immutable())) {
						continue;
					}

					BlockState state = level.getBlockState(position);
					int priority = priorities.getOrDefault(state.getBlock(), 0);
					if (priority <= 0 || state.isAir()) {
						continue;
					}
					double hardness = getBlockStrength(state, level, position);
					if (!Double.isFinite(hardness) || hardness < 0.0D || !hasVisibleSide(level, player, position)) {
						continue;
					}

					double angleChange = AngleUtil.getNeededChange(
							AngleUtil.getPlayerAngle(),
							AngleUtil.getRotation(player.getEyePosition(), center)
					).lengthSqrt();
					double score = score(hardness, miningSpeed, angleChange, distanceSquared, priority);
					candidates.add(
							new BlockCandidate(position.immutable(), state, score, hardness, distanceSquared, angleChange),
							score
					);
				}
			}
		}
	}

	private static boolean hasVisibleSide(ClientLevel level, LocalPlayer player, BlockPos position) {
		for (Direction side : Direction.values()) {
			if (RaytracingUtil.canSeePoint(
					level,
					player,
					player.getEyePosition(),
					pointOnSide(position, side, 0.5D, 0.5D)
			)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isSpaceClear(ClientLevel level, BlockPos position) {
		BlockState state = level.getBlockState(position);
		return state.getFluidState().isEmpty() && state.getCollisionShape(level, position).isEmpty();
	}

	private static Vec3 pointOnSide(BlockPos position, Direction side, double a, double b) {
		double x = position.getX() + 0.5D;
		double y = position.getY() + 0.5D;
		double z = position.getZ() + 0.5D;
		switch (side) {
			case DOWN -> {
				y = position.getY() + 0.001D;
				x += a - 0.5D;
				z += b - 0.5D;
			}
			case UP -> {
				y = position.getY() + 0.999D;
				x += a - 0.5D;
				z += b - 0.5D;
			}
			case NORTH -> {
				z = position.getZ() + 0.001D;
				x += a - 0.5D;
				y += b - 0.5D;
			}
			case SOUTH -> {
				z = position.getZ() + 0.999D;
				x += a - 0.5D;
				y += b - 0.5D;
			}
			case WEST -> {
				x = position.getX() + 0.001D;
				y += a - 0.5D;
				z += b - 0.5D;
			}
			case EAST -> {
				x = position.getX() + 0.999D;
				y += a - 0.5D;
				z += b - 0.5D;
			}
		}
		return new Vec3(x, y, z);
	}
}
