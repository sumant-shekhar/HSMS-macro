package com.example.blockminer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import com.example.blockminer.MiningTargets.Mode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

final class MinerState {
	private static final Logger LOGGER = LoggerFactory.getLogger("blockminer");
	private static final int STUCK_TIMEOUT_TICKS = 100;

	private final Map<BlockPos, Long> blacklistUntilTick = new HashMap<>();
	private boolean enabled;
	private @NonNull Mode selectedMode = Mode.MITHRIL;
	private MiningTargets.Target target;
	private int ticksOnTarget;
	private long clientTick;
	private boolean mining;
	private String searchReason = "no target in range (4 blocks)";

	static void setLeftClick(boolean pressed) {
		BlockMinerClient.setLeftClick(pressed);
	}

	static void triggerLeftClick() {
		BlockMinerClient.triggerLeftClick();
	}

	static void setRightClick(boolean pressed) {
		BlockMinerClient.setRightClick(pressed);
	}

	static void triggerRightClick() {
		BlockMinerClient.triggerRightClick();
	}

	static void releaseAllKeys() {
		BlockMinerClient.releaseAllKeys();
	}

	boolean isEnabled() {
		return enabled;
	}

	@NonNull String getStatus() {
		if (target == null) {
			return "Block Miner: ON | " + selectedMode.displayName() + " | " + searchReason;
		}

		BlockPos position = target.position();
		return "Block Miner: ON | " + selectedMode.displayName() + " | score " + String.format("%.0f", target.score())
				+ " | " + position.getX() + " " + position.getY() + " " + position.getZ();
	}

	@NonNull Mode getSelectedMode() {
		return selectedMode;
	}

	void selectMode(Minecraft client, @NonNull Mode mode) {
		if (selectedMode != mode) {
			selectedMode = mode;
			blacklistUntilTick.clear();
			clearTarget(client);
		}
	}

	void start(Minecraft client) {
		if (!enabled) {
			LOGGER.info("[blockminer] mining started");
		}
		enabled = true;
		blacklistUntilTick.clear();
		clearTarget(client);
	}

	void stop(Minecraft client) {
		if (enabled) {
			LOGGER.info("[blockminer] mining stopped");
		}
		enabled = false;
		blacklistUntilTick.clear();
		clearTarget(client);
		releaseAllKeys();
	}

	boolean toggle(Minecraft client) {
		if (enabled) {
			stop(client);
		} else {
			start(client);
		}
		return enabled;
	}

	void tick(Minecraft client) {
		ClientLevel level = client.level;
		LocalPlayer player = client.player;
		if (!enabled) {
			releaseMining();
			return;
		}

		clientTick++;
		removeExpiredBlacklistEntries();

		if (client.gui.screen() != null) {
			releaseMining();
			releaseAllKeys();
			clearTarget(client);

			if (!(client.gui.screen() instanceof ChatScreen)
					&& !(client.gui.screen() instanceof MinerSettingsScreen)) {
				stop(client);
			}
			return;
		}

		if (player == null || level == null || !player.isAlive()) {
			stop(client);
			return;
		}
		level = Objects.requireNonNull(level);
		player = Objects.requireNonNull(player);

		if (target != null) {
			BlockState targetState = level.getBlockState(target.position());
			if (targetState.isAir()
					|| targetState.is(Blocks.BEDROCK)
					|| !MiningTargets.isTargetBlock(targetState.getBlock(), selectedMode)
					|| !MiningTargets.isInReach(player, target.position())
					|| !MiningTargets.isInView(player, target.aimPoint())) {
				clearTarget(client);
				return;
			}
		}

		if (target == null) {
			MiningTargets.SearchResult search =
					MiningTargets.findTarget(client, player, blacklistUntilTick.keySet(), selectedMode);

			if (search.target() != null) {
				target = search.target();
				searchReason = search.reason();
				ticksOnTarget = 0;
			} else {
				searchReason = search.reason();
				releaseMining();
				if (clientTick % 20 == 0) {
					LOGGER.info("[blockminer] {}", searchReason);
				}
				return;
			}
		}

		if (++ticksOnTarget > STUCK_TIMEOUT_TICKS) {
			blacklistUntilTick.put(target.position(), clientTick + STUCK_TIMEOUT_TICKS);
			clearTarget(client);
			return;
		}

		boolean aimed = RotationHelper.rotateToward(player, target.aimPoint());

		if (!aimed || !(client.hitResult instanceof BlockHitResult hit)
				|| hit.getType() != HitResult.Type.BLOCK
				|| !hit.getBlockPos().equals(target.position())) {
			releaseMining();
			return;
		}

		if (!mining) {
			mining = true;
			LOGGER.info("[blockminer] started breaking block at {}", target.position());
		}
		setLeftClick(true);
	}

	private void removeExpiredBlacklistEntries() {
		Iterator<Map.Entry<BlockPos, Long>> entries = blacklistUntilTick.entrySet().iterator();
		while (entries.hasNext()) {
			if (entries.next().getValue() <= clientTick) {
				entries.remove();
			}
		}
	}

	private void clearTarget(Minecraft client) {
		releaseMining();
		target = null;
		ticksOnTarget = 0;
	}

	private void releaseMining() {
		setLeftClick(false);
		if (mining) {
			LOGGER.info("[blockminer] stopped breaking block at {}", target == null ? "unknown" : target.position());
		}
		mining = false;
	}
}
