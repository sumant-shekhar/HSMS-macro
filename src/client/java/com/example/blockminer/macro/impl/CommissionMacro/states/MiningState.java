package com.example.blockminer.macro.impl.CommissionMacro.states;

import com.example.blockminer.BlockMiner;
import com.example.blockminer.MineableBlock;
import com.example.blockminer.macro.impl.CommissionMacro.Commission;
import com.example.blockminer.macro.impl.CommissionMacro.CommissionMacro;
import com.example.blockminer.util.FeatureInventory;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Block;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MiningState implements CommissionMacroState {
	private Commission activeCommission;

	@Override
	public void onStart(CommissionMacro macro) {
		activeCommission = macro.getCurrentCommission();
		if (!FeatureInventory.holdMiningTool(Minecraft.getInstance())) {
			macro.disable("Mining tool is missing from hotbar slot 2.");
			return;
		}
		Map<Block, Integer> priorities = new LinkedHashMap<>();
		int mithrilPriority = activeCommission != null && activeCommission.isTitanium() ? 1 : 10;
		for (MineableBlock type : new MineableBlock[]{
				MineableBlock.GRAY_MITHRIL, MineableBlock.GREEN_MITHRIL,
				MineableBlock.BLUE_MITHRIL, MineableBlock.TITANIUM
		}) {
			int priority = type == MineableBlock.TITANIUM ? (mithrilPriority == 1 ? 20 : 1) : mithrilPriority;
			type.blocks().forEach(block -> priorities.put(block, priority));
		}
		BlockMiner.getInstance().start(priorities, macro.getMiningSpeed());
	}

	@Override
	public CommissionMacroState onTick(CommissionMacro macro) {
		Integer remainingFuel = macro.getDrillRemainingFuel();
		if (remainingFuel != null && remainingFuel <= 100) {
			if (com.example.blockminer.Config.drillRefuel) {
				return new RefuelState();
			}
			macro.disable("The drill has 100 fuel or less and automatic refueling is disabled.");
			return this;
		}
		if (macro.getCurrentCommission() == Commission.COMMISSION_CLAIM) {
			return new PathingState();
		}
		if (macro.getCurrentCommission() != null && macro.getCurrentCommission() != activeCommission) {
			return new PathingState();
		}
		BlockMiner miner = BlockMiner.getInstance();
		if (miner.isEnabled()) {
			return this;
		}
		return switch (miner.getError()) {
			case NONE -> {
				macro.disable("Block miner stopped unexpectedly during commission mining.");
				yield this;
			}
			case NO_POINTS_FOUND -> new MiningState();
			case NOT_ENOUGH_BLOCKS -> new StartingState();
			case NO_TARGET_BLOCKS -> {
				macro.disable("No commission mining targets are available.");
				yield this;
			}
			case STUCK -> {
				macro.disable("Block miner stopped after becoming stuck.");
				yield this;
			}
		};
	}

	@Override
	public void onEnd(CommissionMacro macro) {
		BlockMiner.getInstance().stop();
	}
}
