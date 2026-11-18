package com.example.blockminer.pathfinder;

import com.example.blockminer.Angle;
import com.example.blockminer.AngleUtil;
import com.example.blockminer.KeyBindUtil;
import com.example.blockminer.PlayerUtil;
import com.example.blockminer.RotationConfiguration;
import com.example.blockminer.RotationHandler;
import com.example.blockminer.Target;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class PathExecutor {
	private static final PathExecutor INSTANCE = new PathExecutor();
	private static final Logger LOGGER = LoggerFactory.getLogger("blockminer");
	private static final double ARRIVAL_RADIUS = 0.85;
	private static final long STUCK_TIMEOUT_NANOS = 6_000_000_000L;
	private static final long UNSTUCK_ATTEMPT_NANOS = 2_000_000_000L;

	private List<PathNode> nodes = List.of();
	private int targetIndex;
	private long lastProgressNanos;
	private double lastTargetDistance = Double.POSITIVE_INFINITY;
	private boolean running;

	private PathExecutor() {
	}

	public static PathExecutor getInstance() {
		return INSTANCE;
	}

	public void start(PathResult result) {
		List<PathNode> nextNodes = result.getNodes();
		if (nextNodes.isEmpty()) {
			throw new IllegalArgumentException("Cannot follow an empty path.");
		}
		stopInternal();
		nodes = nextNodes;
		targetIndex = Math.min(1, nodes.size() - 1);
		lastProgressNanos = System.nanoTime();
		lastTargetDistance = Double.POSITIVE_INFINITY;
		running = true;
	}

	public void tick(Minecraft client) {
		if (!running) {
			return;
		}
		LocalPlayer player = client.player;
		if (player == null || client.level == null) {
			stopInternal();
			LOGGER.warn("[blockminer] path following stopped because the player or world is unavailable");
			return;
		}
		if (client.gui.screen() != null) {
			stopInternal();
			player.sendSystemMessage(Component.literal("Path following stopped because a screen was opened."));
			return;
		}

		PathNode target = nodes.get(targetIndex);
		double targetX = target.x + 0.5;
		double targetZ = target.z + 0.5;
		double distance = Math.hypot(player.getX() - targetX, player.getZ() - targetZ);
		double targetFeetY = target.y + 1.0;
		boolean atTargetHeight = Math.abs(player.getY() - targetFeetY) < 1.25;

		if (targetIndex == nodes.size() - 1 && distance <= ARRIVAL_RADIUS && atTargetHeight) {
			stopInternal();
			player.sendSystemMessage(Component.literal("Path complete."));
			LOGGER.info("[blockminer] path following completed");
			return;
		}

		if (targetIndex < nodes.size() - 1) {
			PathNode next = nodes.get(targetIndex + 1);
			double nextDistance = Math.hypot(player.getX() - (next.x + 0.5), player.getZ() - (next.z + 0.5));
			if ((distance <= 0.85 && atTargetHeight) || nextDistance < distance) {
				targetIndex++;
				target = nodes.get(targetIndex);
				targetX = target.x + 0.5;
				targetZ = target.z + 0.5;
				distance = Math.hypot(player.getX() - targetX, player.getZ() - targetZ);
				lastProgressNanos = System.nanoTime();
				lastTargetDistance = Double.POSITIVE_INFINITY;
			}
		}

		long now = System.nanoTime();
		if (distance + 0.05 < lastTargetDistance) {
			lastTargetDistance = distance;
			lastProgressNanos = now;
		} else if (now - lastProgressNanos > STUCK_TIMEOUT_NANOS) {
			stopInternal();
			player.sendSystemMessage(Component.literal("Path following stopped: no movement progress."));
			LOGGER.warn("[blockminer] path following stopped after no progress for six seconds");
			return;
		}

		// Find look-ahead node 3-5 blocks ahead for camera steering
		PathNode lookTarget = target;
		for (int i = targetIndex; i < nodes.size(); i++) {
			PathNode candidate = nodes.get(i);
			double distFromPlayer = Math.hypot(player.getX() - (candidate.x + 0.5), player.getZ() - (candidate.z + 0.5));
			if (distFromPlayer >= 3.5 || i == nodes.size() - 1) {
				lookTarget = candidate;
				break;
			}
		}

		Vec3 rotationTarget = new Vec3(lookTarget.x + 0.5, player.getY() + 0.5, lookTarget.z + 0.5);
		Angle targetAngle = AngleUtil.getRotation(rotationTarget);
		float yawDifference = Math.abs(AngleUtil.getNeededYawChange(player.getYRot(), targetAngle.getYaw()));
		RotationHandler rotations = RotationHandler.getInstance();
		if (yawDifference > 5.0F && !rotations.isEnabled()) {
			rotations.queueRotation(new RotationConfiguration(
					new Target(rotationTarget),
					250L,
					RotationConfiguration.RotationType.CLIENT,
					null
			)).start();
		}

		boolean walking = yawDifference <= 70.0F;
		BlockPos playerSupport = PlayerUtil.getBlockStandingOn(player);
		boolean stepUp = target.y > playerSupport.getY();
		boolean wallCollision = player.horizontalCollision;
		boolean inWater = player.isInWater();
		boolean stuckLedge = (now - lastProgressNanos > UNSTUCK_ATTEMPT_NANOS);
		boolean shouldJump = (stepUp || wallCollision || inWater || stuckLedge) && walking;

		KeyBindUtil.setKeyBindState(client.options.keyUp, walking);
		KeyBindUtil.setKeyBindState(client.options.keySprint, walking && yawDifference < 35.0F && !shouldJump);
		KeyBindUtil.setKeyBindState(client.options.keyJump, shouldJump && player.onGround());
	}

	public void stop() {
		stopInternal();
	}

	public boolean isRunning() {
		return running;
	}

	private void stopInternal() {
		boolean wasRunning = running;
		running = false;
		nodes = List.of();
		targetIndex = 0;
		lastProgressNanos = 0L;
		lastTargetDistance = Double.POSITIVE_INFINITY;
		Minecraft client = Minecraft.getInstance();
		KeyBindUtil.setKeyBindState(client.options.keyUp, false);
		KeyBindUtil.setKeyBindState(client.options.keySprint, false);
		KeyBindUtil.setKeyBindState(client.options.keyJump, false);
		if (wasRunning) {
			RotationHandler.getInstance().stop();
		}
	}
}
