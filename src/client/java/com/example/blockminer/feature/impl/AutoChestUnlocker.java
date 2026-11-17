package com.example.blockminer.feature.impl;

import com.example.blockminer.BlockUtil;
import com.example.blockminer.RotationConfiguration;
import com.example.blockminer.RotationHandler;
import com.example.blockminer.Target;
import com.example.blockminer.feature.AbstractFeature;
import com.example.blockminer.util.FeatureInventory;
import com.example.blockminer.util.Strings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class AutoChestUnlocker extends AbstractFeature {
	private static final AutoChestUnlocker INSTANCE = new AutoChestUnlocker();
	private static final int SCAN_INTERVAL_TICKS = 10;
	private final Set<BlockPos> seenChests = new LinkedHashSet<>();
	private final List<BlockPos> chestQueue = new ArrayList<>();
	private ChestFailure chestFailure = ChestFailure.NONE;
	private State state = State.STARTING;
	private BlockPos chestSolving;
	private boolean clickChest;
	private int ticksUntilScan;
	private int attempts;
	private long lastClickNanos;
	private ClientLevel scannedLevel;

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
		start("", clickChest);
	}

	public void start(String itemToHold, boolean clickChest) {
		if (mc.player == null || mc.level == null) {
			sendError("Chest scanning requires an active world.");
			return;
		}
		stop();
		if (itemToHold != null && !itemToHold.isBlank()) {
			FeatureInventory.holdHotbarItem(mc, itemToHold);
		}
		this.clickChest = clickChest;
		chestFailure = ChestFailure.NONE;
		enabled = true;
		state = State.STARTING;
		scannedLevel = mc.level;
		scanNearbyChests();
		note("Started nearby chest polling.");
		if (!clickChest) {
			// TODO(verify): Identify a supported particle-observation hook before implementing the lockpick puzzle.
			warn("Particle-guided lockpicking is unavailable without the reference particle hook; click mode is disabled.");
		}
	}

	@Override
	public void stop() {
		enabled = false;
		RotationHandler.getInstance().stop();
		chestSolving = null;
		chestQueue.clear();
		seenChests.clear();
		clickChest = false;
		attempts = 0;
		state = State.STARTING;
	}

	@Override
	public void resetStatesAfterStop() {
		state = State.STARTING;
	}

	public ChestFailure getChestFailure() {
		return chestFailure;
	}

	public List<BlockPos> getQueuedChests() {
		return List.copyOf(chestQueue);
	}

	@Override
	protected void onChat(String message) {
		if (enabled && message.equals(Strings.CHEST_LOCKPICKED_CHAT)) {
			log("Chest unlock message received.");
			chestQueue.remove(chestSolving);
			chestSolving = null;
			state = State.STARTING;
		}
	}

	@Override
	protected void onTick(Minecraft client) {
		if (client.player == null || client.level == null || client.gameMode == null) {
			stop();
			return;
		}
		if (client.level != scannedLevel) {
			chestQueue.clear();
			seenChests.clear();
			chestSolving = null;
			scannedLevel = client.level;
		}
		if (--ticksUntilScan <= 0) {
			scanNearbyChests();
			ticksUntilScan = SCAN_INTERVAL_TICKS;
		}

		if (chestSolving != null && !isChest(client, chestSolving)) {
			chestQueue.remove(chestSolving);
			chestSolving = null;
			state = State.STARTING;
		}

		switch (state) {
			case STARTING -> selectChest(client);
			case ROTATING -> aimOrClick(client);
		}
	}

	private void scanNearbyChests() {
		if (mc.player == null || mc.level == null) {
			return;
		}
		BlockPos center = BlockPos.containing(mc.player.getX(), mc.player.getY(), mc.player.getZ());
		for (int x = -6; x <= 6; x++) {
			for (int y = -4; y <= 4; y++) {
				for (int z = -6; z <= 6; z++) {
					BlockPos position = center.offset(x, y, z).immutable();
					if (isChest(mc, position)) {
						if (seenChests.add(position) && !chestQueue.contains(position)) {
							chestQueue.add(position);
						}
					} else {
						seenChests.remove(position);
					}
				}
			}
		}
	}

	private void selectChest(Minecraft client) {
		chestQueue.removeIf(position -> !isChest(client, position));
		if (chestQueue.isEmpty()) {
			return;
		}
		chestQueue.sort(Comparator.comparingDouble(position ->
				client.player.distanceToSqr(Vec3.atCenterOf(position))));
		chestSolving = chestQueue.getFirst();
		attempts = 0;
		lastClickNanos = 0L;
		if (client.player.getEyePosition().distanceTo(Vec3.atCenterOf(chestSolving)) > 4.5D) {
			chestSolving = null;
			return;
		}
		state = State.ROTATING;
	}

	private void aimOrClick(Minecraft client) {
		if (chestSolving == null || !isChest(client, chestSolving)) {
			state = State.STARTING;
			return;
		}
		if (FeatureInventory.menu(client) != null) {
			return;
		}
		if (client.hitResult instanceof BlockHitResult hit
				&& hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(chestSolving)) {
			if (clickChest) {
				long now = System.nanoTime();
				if (now - lastClickNanos < 250_000_000L) {
					return;
				}
				lastClickNanos = now;
				client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
				attempts++;
				if (attempts >= 3) {
					sendError("No unlock response arrived after three clicks; removing this chest from the queue.");
					chestFailure = ChestFailure.NO_UNLOCK_RESPONSE;
					chestQueue.remove(chestSolving);
					chestSolving = null;
					state = State.STARTING;
				} else {
					RotationHandler.getInstance().stop();
				}
			} else {
				warn("This chest requires particle-guided lockpicking, which is not implemented without a verified particle hook.");
				chestQueue.remove(chestSolving);
				chestSolving = null;
				state = State.STARTING;
			}
			return;
		}

		if (!RotationHandler.getInstance().isEnabled()) {
			List<Vec3> visiblePoints = BlockUtil.bestPointsOnBestSide(client.level, client.player, chestSolving);
			if (visiblePoints.isEmpty()) {
				chestQueue.remove(chestSolving);
				chestSolving = null;
				state = State.STARTING;
				return;
			}
			RotationHandler.getInstance().easeTo(new RotationConfiguration(
					new Target(visiblePoints.getFirst()), 400L, null));
		}
	}

	private static boolean isChest(Minecraft client, BlockPos position) {
		if (client.level == null) {
			return false;
		}
		var block = client.level.getBlockState(position).getBlock();
		return block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST;
	}

	public enum ChestFailure {
		NONE,
		NO_UNLOCK_RESPONSE
	}

	private enum State {
		STARTING,
		ROTATING
	}
}
