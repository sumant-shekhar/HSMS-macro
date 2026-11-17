package com.example.blockminer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.Locale;
import java.util.Set;

/**
 * Scores visible Mithril and selected vanilla block targets.
 *
 * Lower score wins:
 *
 *   score = 1 / miningSpeed * 200
 *          + rotationAngle
 *          + distanceSquared * 10
 *
 * Only candidates already within the camera's view cone and line of sight
 * are considered.
 */
final class MiningTargets {
	static final int SEARCH_RADIUS = 5;
	static final double REACH = 4.0D;
	static final double REACH_SQUARED = REACH * REACH;
	static final double MIN_VIEW_ANGLE = 30.0D;
	static final double MAX_VIEW_ANGLE = 180.0D;
	private static double maxViewAngle = 90.0D;

	private static final double MINING_COEFFICIENT = 200.0D;
	private static final double ANGLE_COEFFICIENT = 1.0D;
	private static final double DISTANCE_COEFFICIENT = 10.0D;

	private MiningTargets() {
	}

	static double getMaxViewAngle() {
		return maxViewAngle;
	}

	static void setMaxViewAngle(double degrees) {
		maxViewAngle = Math.max(MIN_VIEW_ANGLE, Math.min(MAX_VIEW_ANGLE, degrees));
	}

	static SearchResult findTarget(
			Minecraft client,
			LocalPlayer player,
			Set<BlockPos> blacklist,
			MiningTargets.@NonNull Mode mode
	) {
		ClientLevel level = client.level;
		if (level == null) {
			return new SearchResult(null, "no target in range (4 blocks)");
		}
		level = Objects.requireNonNull(level);

		BlockPos origin = player.blockPosition();
		BlockPos min = origin.offset(-SEARCH_RADIUS, -SEARCH_RADIUS, -SEARCH_RADIUS);
		BlockPos max = origin.offset(SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS);

		Target best = null;
		int selectedTargetsInReach = 0;
		int blacklistedTargetsInReach = 0;
		boolean otherModeBlockInReach = false;
		boolean hasLineOfSight = false;
		boolean hasTargetInView = false;

		for (BlockPos mutablePosition : BlockPos.betweenClosed(min, max)) {
			@NonNull BlockPos position = mutablePosition.immutable();
			BlockState state = level.getBlockState(position);
			@NonNull Block block = Objects.requireNonNull(state.getBlock());
			if (!isTargetBlock(block, mode)) {
				if (isModeBlock(block)
						&& player.getEyePosition().distanceToSqr(Vec3.atCenterOf(position)) <= REACH_SQUARED) {
					otherModeBlockInReach = true;
				}
				continue;
			}

			Vec3 center = Vec3.atCenterOf(position);
			double distanceSquared = player.getEyePosition().distanceToSqr(center);
			if (distanceSquared > REACH_SQUARED) {
				continue;
			}
			selectedTargetsInReach++;
			if (blacklist.contains(position)) {
				blacklistedTargetsInReach++;
				continue;
			}

			AimPoint aim = bestAimPoint(level, player, position);
			if (aim == null) {
				continue;
			}
			hasLineOfSight = true;
			if (viewAngle(player, aim.point()) > maxViewAngle) {
				continue;
			}
			hasTargetInView = true;

			double miningSpeed = state.getDestroySpeed(level, position);
			if (miningSpeed <= 0.0D || Double.isNaN(miningSpeed)) {
				miningSpeed = 0.1D;
			}

			double angleCost = aim.viewAngle();
			double score =
					(MINING_COEFFICIENT / miningSpeed)
					+ angleCost * ANGLE_COEFFICIENT
					+ distanceSquared * DISTANCE_COEFFICIENT;

			Target candidate = new Target(position, score, aim.point());
			if (best == null || candidate.score() < best.score()) {
				best = candidate;
			}
		}

		if (best != null) {
			return new SearchResult(best, "target acquired");
		}

		String reason;
		if (selectedTargetsInReach == 0) {
			reason = mode == Mode.MITHRIL ? "no registered Mithril block in range (4 blocks)"
					: otherModeBlockInReach ? "block isn't the selected mode" : "no target in range (4 blocks)";
		} else if (blacklistedTargetsInReach == selectedTargetsInReach) {
			reason = "target temporarily blacklisted";
		} else if (!hasLineOfSight) {
			reason = "no line of sight";
		} else if (!hasTargetInView) {
			reason = "outside the " + Math.round(maxViewAngle) + " degree view cone";
		} else {
			reason = "no target in range (4 blocks)";
		}
		return new SearchResult(null, reason);
	}

	static boolean isTargetBlock(@NonNull Block block, MiningTargets.@NonNull Mode mode) {
		return mode.matches(block);
	}

	static boolean isInReach(LocalPlayer player, @NonNull BlockPos position) {
		return player.getEyePosition().distanceToSqr(Vec3.atCenterOf(position)) <= REACH_SQUARED;
	}

	static boolean isInView(LocalPlayer player, @NonNull Vec3 point) {
		return viewAngle(player, point) <= maxViewAngle;
	}

	private static boolean isModeBlock(@NonNull Block block) {
		for (MiningTargets.@NonNull Mode candidate : Mode.values()) {
			if (isTargetBlock(block, candidate)) {
				return true;
			}
		}
		return false;
	}

	private static double viewAngle(LocalPlayer player, @NonNull Vec3 point) {
		Vec3 direction = point.subtract(player.getEyePosition()).normalize();
		double dot = player.getLookAngle().normalize().dot(direction);
		return Math.toDegrees(Math.acos(Math.max(-1.0D, Math.min(1.0D, dot))));
	}

	/**
	 * Finds the visible point requiring the least camera rotation.
	 * Sampling multiple points on every face avoids always aiming at the
	 * exact block center and more closely follows MightyMiner's face/point
	 * selection behavior.
	 */
	private static AimPoint bestAimPoint(ClientLevel level, LocalPlayer player, @NonNull BlockPos position) {
		AimPoint best = null;

		for (Direction direction : Direction.values()) {
			for (double a : new double[] {-0.22D, 0.0D, 0.22D}) {
				for (double b : new double[] {-0.22D, 0.0D, 0.22D}) {
					Vec3 point = facePoint(position, direction, a, b);

					if (!hasLineOfSight(level, player, position, point)) {
						continue;
					}

					double angle = viewAngle(player, point);
					if (best == null || angle < best.viewAngle()) {
						best = new AimPoint(point, angle);
					}
				}
			}
		}

		return best;
	}

	private static @NonNull Vec3 facePoint(@NonNull BlockPos position, Direction direction, double a, double b) {
		double x = position.getX() + 0.5D;
		double y = position.getY() + 0.5D;
		double z = position.getZ() + 0.5D;

		switch (direction) {
			case UP -> {
				y = position.getY() + 0.999D;
				x += a;
				z += b;
			}
			case DOWN -> {
				y = position.getY() + 0.001D;
				x += a;
				z += b;
			}
			case NORTH -> {
				z = position.getZ() + 0.001D;
				x += a;
				y += b;
			}
			case SOUTH -> {
				z = position.getZ() + 0.999D;
				x += a;
				y += b;
			}
			case WEST -> {
				x = position.getX() + 0.001D;
				y += a;
				z += b;
			}
			case EAST -> {
				x = position.getX() + 0.999D;
				y += a;
				z += b;
			}
		}

		return new Vec3(x, y, z);
	}

	private static boolean hasLineOfSight(
			ClientLevel level,
			LocalPlayer player,
			@NonNull BlockPos position,
			@NonNull Vec3 point
	) {
		BlockHitResult hit = level.clip(new ClipContext(
				player.getEyePosition(),
				point,
				ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE,
				player
		));

		return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(position);
	}

	record Target(@NonNull BlockPos position, double score, @NonNull Vec3 aimPoint) {
	}

	record SearchResult(Target target, String reason) {
	}

	enum Mode {
		MITHRIL("Mithril",
				Blocks.WOOL.gray(),
				Blocks.DYED_TERRACOTTA.cyan(),
				Blocks.PRISMARINE,
				Blocks.PRISMARINE_BRICKS,
				Blocks.DARK_PRISMARINE,
				Blocks.WOOL.lightBlue()),
		TITANIUM("Titanium", Blocks.POLISHED_DIORITE),
		COAL("Coal Block", Blocks.COAL_BLOCK),
		IRON("Iron Block", Blocks.IRON_BLOCK),
		GOLD("Gold Block", Blocks.GOLD_BLOCK),
		LAPIS("Lapis Block", Blocks.LAPIS_BLOCK),
		REDSTONE("Redstone Block", Blocks.REDSTONE_BLOCK),
		EMERALD("Emerald Block", Blocks.EMERALD_BLOCK),
		DIAMOND("Diamond Block", Blocks.DIAMOND_BLOCK);

		private final @NonNull String displayName;
		private final Block[] blocks;

		Mode(@NonNull String displayName, Block... blocks) {
			this.displayName = displayName;
			this.blocks = blocks;
		}

		@NonNull String displayName() {
			return displayName;
		}

		boolean matches(@NonNull Block target) {
			for (Block block : blocks) {
				if (block == target) {
					return true;
				}
			}

			if (this == MITHRIL) {
				Identifier identifier = BuiltInRegistries.BLOCK.getKey(target);
				return (identifier.getNamespace() + ":" + identifier.getPath()).toLowerCase(Locale.ROOT)
						.contains("mithril");
			}
			return false;
		}
	}

	private record AimPoint(@NonNull Vec3 point, double viewAngle) {
	}
}
