package com.example.blockminer.feature.impl;

import com.example.blockminer.BlockMinerClient;
import com.example.blockminer.Config;
import com.example.blockminer.KeyBindUtil;
import com.example.blockminer.RotationConfiguration;
import com.example.blockminer.RotationHandler;
import com.example.blockminer.RotationHelper;
import com.example.blockminer.Target;
import com.example.blockminer.feature.AbstractFeature;
import com.example.blockminer.handler.RouteHandler;
import com.example.blockminer.macro.MacroManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AutoChestUnlocker extends AbstractFeature {
	private static final AutoChestUnlocker INSTANCE = new AutoChestUnlocker();
	private static final Logger LOGGER = LoggerFactory.getLogger("blockminer/chest");
	private static final long CHEST_TIMEOUT_NANOS = 12_000_000_000L;

	private final Set<BlockPos> solvedChests = Collections.newSetFromMap(new LinkedHashMap<>() {
		@Override
		protected boolean removeEldestEntry(Map.Entry<BlockPos, Boolean> eldest) {
			return size() > 256;
		}
	});

	private final List<BlockPos> chestQueue = new ArrayList<>();
	private State state = State.IDLE;
	private BlockPos currentChest;
	private Vec3 particlePos;
	private long stateStartTimeNanos;
	private long lastParticleNanos;
	private long lastRightClickNanos;
	private int openAttempts;
	private int scanTicks;
	private int chestsUnlocked = 0;
	private boolean pausedMiner;
	private boolean pausedMacro;
	private boolean pausedRoute;

	private AutoChestUnlocker() {
	}

	public static AutoChestUnlocker getInstance() {
		return INSTANCE;
	}

	@Override
	public String getName() {
		return "TreasureChestUnlocker";
	}

	public void start(boolean clickChest) {
		start();
	}

	public void start(String itemToHold, boolean clickChest) {
		start();
	}

	@Override
	public void start() {
		enabled = true;
		Minecraft client = Minecraft.getInstance();
		if (client != null) {
			scanNearbyChests(client);
		}
	}

	@Override
	public boolean shouldStartAtLaunch() {
		return true;
	}

	public State getState() {
		return state;
	}

	public int getChestsUnlocked() {
		return chestsUnlocked;
	}

	public void resetChestsUnlocked() {
		chestsUnlocked = 0;
	}

	public BlockPos getCurrentChest() {
		return currentChest;
	}

	public List<BlockPos> getQueuedChests() {
		return List.copyOf(chestQueue);
	}

	public void onBlockUpdate(BlockPos pos, BlockState state) {
		Minecraft client = Minecraft.getInstance();
		if (!Config.autoChestUnlocker || client == null || client.player == null) {
			return;
		}

		if (state.getBlock() instanceof ChestBlock) {
			double distSq = client.player.distanceToSqr(Vec3.atCenterOf(pos));
			double maxRadius = Config.chestSearchRadius;
			if (distSq <= maxRadius * maxRadius && !solvedChests.contains(pos)
					&& !chestQueue.contains(pos) && !pos.equals(currentChest)) {
				enqueueChest(client, pos.immutable());
			}
		} else if (pos.equals(currentChest) && (state.isAir() || !(state.getBlock() instanceof ChestBlock))) {
			finishChest(true, "Chest block removed / despawned");
		}
	}

	public void onParticle(ParticleType<?> type, double x, double y, double z) {
		BlockMinerClient.getMiner().onParticle(type, x, y, z);

		if (!Config.autoChestUnlocker || state != State.LOCKPICKING || currentChest == null || mc.level == null) {
			return;
		}

		if (type == ParticleTypes.CRIT) {
			BlockState chestState = mc.level.getBlockState(currentChest);
			Direction facing = Direction.NORTH;
			if (chestState.hasProperty(ChestBlock.FACING)) {
				facing = chestState.getValue(ChestBlock.FACING);
			}
			BlockPos frontPos = currentChest.relative(facing);
			Vec3 frontCenter = Vec3.atCenterOf(frontPos);
			Vec3 particle = new Vec3(x, y, z);
			double distToFront = particle.distanceTo(frontCenter);
			double distToChest = particle.distanceTo(Vec3.atCenterOf(currentChest));

			if (distToFront <= 1.5D || distToChest <= 1.8D) {
				particlePos = particle;
				lastParticleNanos = System.nanoTime();
				RotationHandler.getInstance().stopFollowingTarget();
				RotationHandler.getInstance().easeTo(new RotationConfiguration(
						new Target(particle),
						Config.chestRotationMs,
						null
				).followTarget(true));
			}
		}
	}

	@Override
	public void onChat(String message) {
		if (!Config.autoChestUnlocker || currentChest == null) {
			return;
		}
		String clean = message.replaceAll("§[0-9a-fk-or]", "").trim();
		if (clean.contains("CHEST LOCKPICKED")) {
			finishChest(true, "Received CHEST LOCKPICKED chat confirmation");
		}
	}

	@Override
	public void onTick(Minecraft client) {
		if (client.player == null || client.level == null || client.gui.screen() != null) {
			if (state != State.IDLE) {
				stop();
			}
			return;
		}

		if (!Config.autoChestUnlocker) {
			if (state != State.IDLE) {
				stop();
			}
			return;
		}

		if (++scanTicks >= 20) {
			scanTicks = 0;
			scanNearbyChests(client);
		}

		if (state == State.IDLE) {
			if (!chestQueue.isEmpty()) {
				startNextChest(client);
			}
			return;
		}

		long now = System.nanoTime();
		long timeoutNanos = Config.chestTimeoutSeconds > 0
				? (long) Config.chestTimeoutSeconds * 1_000_000_000L
				: CHEST_TIMEOUT_NANOS;
		if (now - stateStartTimeNanos > timeoutNanos) {
			finishChest(false, "Timeout after " + (timeoutNanos / 1_000_000_000L) + "s");
			return;
		}

		if (currentChest == null || !isChest(client, currentChest)) {
			finishChest(true, "Target chest is no longer present");
			return;
		}

		switch (state) {
			case APPROACHING -> tickApproaching(client);
			case OPENING -> tickOpening(client, now);
			case LOCKPICKING -> tickLockpicking(client, now);
			case DONE -> resetToIdle(client);
			case IDLE -> {
			}
		}
	}

	private void tickApproaching(Minecraft client) {
		double distance = client.player.getEyePosition().distanceTo(Vec3.atCenterOf(currentChest));
		if (distance <= 4.2D) {
			KeyBindUtil.stopMovement();
			state = State.OPENING;
			return;
		}

		Vec3 center = Vec3.atCenterOf(currentChest);
		RotationHelper.rotateToward(client.player, center);
		if (Config.chestAutoWalk) {
			KeyBindUtil.setKeyBindState(client.options.keyUp, true);
			KeyBindUtil.setKeyBindState(client.options.keySprint, true);
			if (client.player.horizontalCollision && client.player.onGround()) {
				KeyBindUtil.setKeyBindState(client.options.keyJump, true);
			} else {
				KeyBindUtil.setKeyBindState(client.options.keyJump, false);
			}
		} else {
			KeyBindUtil.stopMovement();
		}
	}

	private void tickOpening(Minecraft client, long now) {
		KeyBindUtil.stopMovement();
		double distance = client.player.getEyePosition().distanceTo(Vec3.atCenterOf(currentChest));
		if (distance > 4.6D) {
			state = State.APPROACHING;
			return;
		}

		Vec3 center = Vec3.atCenterOf(currentChest);
		boolean aimed = RotationHelper.rotateToward(client.player, center);

		if ((aimed || now - stateStartTimeNanos > 800_000_000L) && now - lastRightClickNanos >= 350_000_000L) {
			lastRightClickNanos = now;
			openAttempts++;
			KeyBindUtil.rightClick();
			state = State.LOCKPICKING;
			lastParticleNanos = now;
		}
	}

	private void tickLockpicking(Minecraft client, long now) {
		KeyBindUtil.stopMovement();
		if (particlePos != null) {
			RotationHelper.rotateToward(client.player, particlePos);
		}

		if (now - lastParticleNanos > 3_000_000_000L && openAttempts < 4) {
			state = State.OPENING;
		}
	}

	private void enqueueChest(Minecraft client, BlockPos pos) {
		chestQueue.add(pos);
		LOGGER.info("[blockminer] Enqueued chest at {}", pos);
		if (state == State.IDLE) {
			startNextChest(client);
		}
	}

	private void startNextChest(Minecraft client) {
		chestQueue.removeIf(p -> !isChest(client, p) || solvedChests.contains(p));
		if (chestQueue.isEmpty()) {
			state = State.DONE;
			resetToIdle(client);
			return;
		}

		currentChest = chestQueue.removeFirst();
		if (currentChest == null || !isChest(client, currentChest)) {
			startNextChest(client);
			return;
		}

		if (!pausedMiner && !pausedMacro && !pausedRoute) {
			pausedMiner = BlockMinerClient.getMiner().isEnabled();
			pausedMacro = MacroManager.getInstance().isEnabled();
			pausedRoute = RouteNavigator.getInstance().isRunning();
			if (pausedMiner) {
				BlockMinerClient.getMiner().stop(client);
			}
			if (pausedMacro) {
				MacroManager.getInstance().disable();
			}
			if (pausedRoute) {
				RouteNavigator.getInstance().stop();
			}
		}

		KeyBindUtil.releaseAll();
		stateStartTimeNanos = System.nanoTime();
		lastParticleNanos = System.nanoTime();
		lastRightClickNanos = 0L;
		openAttempts = 0;
		particlePos = null;
		enabled = true;

		double distance = client.player.getEyePosition().distanceTo(Vec3.atCenterOf(currentChest));
		if (distance > 4.2D) {
			state = State.APPROACHING;
		} else {
			state = State.OPENING;
		}
		LOGGER.info("[blockminer] Starting chest unlock at {} (dist: {:.2f})", currentChest, distance);
	}

	private void finishChest(boolean success, String reason) {
		if (currentChest != null) {
			solvedChests.add(currentChest);
			LOGGER.info("[blockminer] Finished chest at {} ({}): {}", currentChest, success ? "success" : "abandoned", reason);
			if (success) {
				chestsUnlocked++;
			}
			currentChest = null;
		}

		particlePos = null;
		KeyBindUtil.stopMovement();
		RotationHandler.getInstance().stop();

		Minecraft client = Minecraft.getInstance();
		if (!chestQueue.isEmpty()) {
			startNextChest(client);
		} else {
			state = State.DONE;
			resetToIdle(client);
		}
	}

	private void resetToIdle(Minecraft client) {
		state = State.IDLE;
		currentChest = null;
		particlePos = null;
		enabled = false;
		KeyBindUtil.releaseAll();
		RotationHandler.getInstance().stop();

		if (pausedMiner) {
			pausedMiner = false;
			BlockMinerClient.getMiner().start(client);
		}
		if (pausedMacro) {
			pausedMacro = false;
			MacroManager.getInstance().enable();
		}
		if (pausedRoute) {
			pausedRoute = false;
			RouteNavigator.getInstance().start(RouteHandler.getInstance().getSelectedRoute());
		}
	}

	@Override
	public void stop() {
		state = State.IDLE;
		currentChest = null;
		particlePos = null;
		chestQueue.clear();
		enabled = false;
		KeyBindUtil.releaseAll();
		RotationHandler.getInstance().stop();
		pausedMiner = false;
		pausedMacro = false;
		pausedRoute = false;
	}

	private void scanNearbyChests(Minecraft client) {
		if (client.player == null || client.level == null) {
			return;
		}

		int radius = (int) Math.ceil(Config.chestSearchRadius);
		BlockPos center = client.player.blockPosition();
		double maxRadiusSq = Config.chestSearchRadius * Config.chestSearchRadius;

		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -4, -radius), center.offset(radius, 4, radius))) {
			if (isChest(client, pos)) {
				BlockPos immutable = pos.immutable();
				if (!solvedChests.contains(immutable) && !chestQueue.contains(immutable)
						&& !immutable.equals(currentChest)
						&& client.player.distanceToSqr(Vec3.atCenterOf(immutable)) <= maxRadiusSq) {
					enqueueChest(client, immutable);
				}
			}
		}
	}

	private static boolean isChest(Minecraft client, BlockPos position) {
		if (client == null || client.level == null) {
			return false;
		}
		var block = client.level.getBlockState(position).getBlock();
		return block instanceof ChestBlock;
	}

	public enum State {
		IDLE,
		APPROACHING,
		OPENING,
		LOCKPICKING,
		DONE
	}
}
