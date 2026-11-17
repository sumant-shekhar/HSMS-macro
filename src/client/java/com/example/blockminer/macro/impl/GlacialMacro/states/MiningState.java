package com.example.blockminer.macro.impl.GlacialMacro.states;

import com.example.blockminer.BlockMiner;
import com.example.blockminer.Config;
import com.example.blockminer.macro.impl.GlacialMacro.GlacialMacro;
import com.example.blockminer.util.FeatureInventory;
import net.minecraft.client.Minecraft;

public final class MiningState implements GlacialMacroState {
	private int retries;
	private boolean waitingToRetry;

	@Override
	public void onStart(GlacialMacro macro) {
		retries = 0;
		waitingToRetry = false;
		startMining(macro);
	}

	@Override
	public GlacialMacroState onTick(GlacialMacro macro) {
		if (macro.getCold() >= macro.getColdThreshold()) {
			return new TeleportingState(new PathfindingState());
		}
		if (macro.hasCompletedCommission()) {
			return new ClaimingCommissionState();
		}
		if (waitingToRetry) {
			waitingToRetry = false;
			startMining(macro);
			return this;
		}
		BlockMiner miner = BlockMiner.getInstance();
		if (miner.isEnabled()) {
			return this;
		}
		switch (miner.getError()) {
			case NONE -> {
				macro.disable("Block miner stopped unexpectedly during Glacial mining.");
				return this;
			}
			case NO_POINTS_FOUND -> {
				startMining(macro);
				return this;
			}
			case NOT_ENOUGH_BLOCKS -> {
				if (++retries <= 3) {
					waitingToRetry = true;
					macro.scheduleTimer(5_000L);
					return this;
				}
				if (macro.getCurrentVein() != null) {
					macro.getPreviousVeins().put(macro.getCurrentVein(), System.currentTimeMillis());
				}
				return new PathfindingState();
			}
			case NO_TARGET_BLOCKS -> {
				macro.disable("No incomplete Glacial commission blocks were found.");
				return this;
			}
			case STUCK -> {
				macro.disable("Block miner stopped after becoming stuck.");
				return this;
			}
		}
		return this;
	}

	private void startMining(GlacialMacro macro) {
		if (macro.getBlocksToMine().isEmpty()) {
			macro.disable("No incomplete Glacial commission blocks were found.");
			return;
		}
		if (!FeatureInventory.holdHotbarItem(Minecraft.getInstance(), Config.miningTool)) {
			macro.disable("The configured mining tool is not in the hotbar.");
			return;
		}
		// The current BlockMiner has no translated pickaxe-ability parameter; its retrieved value is retained but not applied.
		BlockMiner.getInstance().start(macro.getBlockPriorities(), macro.getMiningSpeed());
	}

	@Override
	public void onEnd(GlacialMacro macro) {
		BlockMiner.getInstance().stop();
	}
}
