package com.example.blockminer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.client.multiplayer.ClientLevel;
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
	private boolean destroying;
	private String searchReason = "no target in range (4 blocks)";

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
		MultiPlayerGameMode gameMode = client.gameMode;
		if (!enabled) {
		releaseDestroy(client);
			return;
		}

		clientTick++;
		removeExpiredBlacklistEntries();

		if (client.gui.screen() != null) {
			releaseDestroy(client);
			clearTarget(client);

			if (!(client.gui.screen() instanceof ChatScreen)
					&& !(client.gui.screen() instanceof MinerSettingsScreen)) {
				stop(client);
			}
			return;
		}

		if (player == null || level == null || !player.isAlive() || gameMode == null) {
			stop(client);
			return;
		}
		level = Objects.requireNonNull(level);
		player = Objects.requireNonNull(player);
		gameMode = Objects.requireNonNull(gameMode);

		if (target != null) {
			if (!MiningTargets.isTargetBlock(level.getBlockState(target.position()).getBlock(), selectedMode)
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
				releaseDestroy(client);
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

		/*
		 * IMPORTANT: never manipulate Minecraft's KeyMapping state here.
		 * That used to call keyAttack.setDown(...), which can interfere with
		 * normal user input. Instead, use the game-mode block-destruction API
		 * directly. This leaves W/A/S/D, Sprint, Jump, Shift, Ctrl, Backtick,
		 * and the physical mouse bindings under normal Minecraft control.
		 */
		if (!aimed || !(client.hitResult instanceof BlockHitResult hit)
				|| hit.getType() != HitResult.Type.BLOCK
				|| !hit.getBlockPos().equals(target.position())) {
			releaseDestroy(client);
			return;
		}

		if (!destroying) {
			destroying = gameMode.startDestroyBlock(target.position(), hit.getDirection());
			if (destroying) {
				LOGGER.info("[blockminer] started breaking block at {}", target.position());
			}
		} else if (!gameMode.continueDestroyBlock(target.position(), hit.getDirection())) {
			releaseDestroy(client);
		}
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
		releaseDestroy(client);
		target = null;
		ticksOnTarget = 0;
	}

	private void releaseDestroy(Minecraft client) {
		if (destroying && client.gameMode != null) {
			client.gameMode.stopDestroyBlock();
			LOGGER.info("[blockminer] stopped breaking block at {}", target == null ? "unknown" : target.position());
		}
		destroying = false;
	}
}
