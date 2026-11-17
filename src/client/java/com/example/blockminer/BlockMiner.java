package com.example.blockminer;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class BlockMiner {
	private static final Logger LOGGER = LoggerFactory.getLogger("blockminer");
	private static final BlockMiner INSTANCE = new BlockMiner();

	private Map<Block, Integer> priorities = Map.of();
	private BlockMinerState currentState;
	private BlockPos targetBlockPos;
	private Block targetBlock;
	private int miningSpeed;
	private boolean enabled;
	private boolean applyAbilityRequested;
	private Error error = Error.NONE;

	private BlockMiner() {
	}

	public static BlockMiner getInstance() {
		return INSTANCE;
	}

	public void start(Map<Block, Integer> priorities, int miningSpeed) {
		Objects.requireNonNull(priorities, "priorities");
		if (miningSpeed <= 0) {
			throw new IllegalArgumentException("Mining speed must be positive");
		}
		stop();
		this.priorities = Map.of();
		Map<Block, Integer> positivePriorities = new LinkedHashMap<>();
		priorities.forEach((block, priority) -> {
			Objects.requireNonNull(block, "priority block");
			Objects.requireNonNull(priority, "block priority");
			if (priority > 0) {
				positivePriorities.put(block, priority);
			}
		});
		if (positivePriorities.isEmpty()) {
			fail(Error.NO_TARGET_BLOCKS);
			return;
		}

		this.priorities = Map.copyOf(positivePriorities);
		this.miningSpeed = miningSpeed;
		this.enabled = true;
		this.error = Error.NONE;
		this.targetBlockPos = null;
		this.targetBlock = null;
		transitionTo(new StartingState());
		LOGGER.info("[blockminer] started with {} target block types", this.priorities.size());
	}

	public void stop() {
		if (currentState != null) {
			currentState.onEnd(this);
			currentState = null;
		}
		enabled = false;
		targetBlockPos = null;
		targetBlock = null;
	}

	public void tick(Minecraft client) {
		if (!enabled) {
			return;
		}
		if (client.gui.screen() != null) {
			stop();
			return;
		}
		if (client.player == null || client.level == null || client.gameMode == null) {
			stop();
			return;
		}
		if (currentState == null) {
			transitionTo(new StartingState());
		}
		if (!enabled || currentState == null) {
			return;
		}

		BlockMinerState next = currentState.onTick(this);
		if (enabled && next != currentState) {
			transitionTo(next);
		}
	}

	public boolean isEnabled() {
		return enabled;
	}

	public Error getError() {
		return error;
	}

	public Map<Block, Integer> getPriorities() {
		return priorities;
	}

	public BlockPos getTargetBlockPos() {
		return targetBlockPos;
	}

	public Block getTargetBlock() {
		return targetBlock;
	}

	public int getMiningSpeed() {
		return miningSpeed;
	}

	public int getWaitThresholdMs() {
		return Config.waitThresholdMs;
	}

	boolean shouldApplyAbility() {
		return applyAbilityRequested;
	}

	void setApplyAbilityRequested(boolean requested) {
		applyAbilityRequested = requested;
	}

	void setTarget(BlockPos position, Block block) {
		targetBlockPos = Objects.requireNonNull(position).immutable();
		targetBlock = Objects.requireNonNull(block);
	}

	void fail(Error error) {
		this.error = Objects.requireNonNull(error);
		LOGGER.warn("[blockminer] stopped: {}", error);
		stop();
	}

	private void transitionTo(BlockMinerState nextState) {
		if (currentState != null) {
			currentState.onEnd(this);
		}
		currentState = nextState;
		if (currentState != null) {
			currentState.onStart(this);
		} else {
			enabled = false;
		}
	}

	public enum Error {
		NONE,
		NOT_ENOUGH_BLOCKS,
		NO_POINTS_FOUND,
		NO_TARGET_BLOCKS,
		STUCK
	}
}
