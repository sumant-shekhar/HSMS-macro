package com.example.blockminer.macro.impl.RouteMiner.states;

import com.example.blockminer.BlockMiner;
import com.example.blockminer.macro.impl.RouteMiner.RouteMinerMacro;
import com.example.blockminer.Config;
import com.example.blockminer.util.FeatureInventory;
import net.minecraft.client.Minecraft;

public final class MiningState implements RouteMinerState {
	@Override
	public void onStart(RouteMinerMacro macro) {
		if (!FeatureInventory.holdHotbarItem(Minecraft.getInstance(), Config.miningTool)) {
			macro.disable("The configured mining tool is not in the hotbar.");
			return;
		}
		BlockMiner.getInstance().start(macro.getBlockPriorities(), macro.getMiningSpeed());
	}

	@Override
	public RouteMinerState onTick(RouteMinerMacro macro) {
		BlockMiner miner = BlockMiner.getInstance();
		if (miner.isEnabled()) {
			return this;
		}
		switch (miner.getError()) {
			case NONE -> {
				macro.disable("Block miner stopped unexpectedly.");
			}
			case NO_POINTS_FOUND -> {
				return new MiningState();
			}
			case NOT_ENOUGH_BLOCKS -> {
				macro.setRouteIndex(macro.getTargetRouteIndex());
				return new MovingState();
			}
			case NO_TARGET_BLOCKS -> macro.disable("No mining blocks are selected.");
			case STUCK -> macro.disable("Block miner stopped after becoming stuck.");
		}
		return this;
	}

	@Override
	public void onEnd(RouteMinerMacro macro) {
		BlockMiner.getInstance().stop();
	}
}
