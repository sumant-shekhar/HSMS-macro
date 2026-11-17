package com.example.blockminer.macro.impl;

import com.example.blockminer.BlockMiner;
import com.example.blockminer.Config;
import com.example.blockminer.MineableBlock;
import com.example.blockminer.feature.impl.AutoGetStats.AutoGetStats;
import com.example.blockminer.feature.impl.AutoGetStats.tasks.impl.MiningSpeedRetrievalTask;
import com.example.blockminer.feature.impl.AutoGetStats.tasks.impl.PickaxeAbilityRetrievalTask;
import com.example.blockminer.macro.AbstractMacro;
import com.example.blockminer.util.FeatureInventory;
import net.minecraft.client.Minecraft;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MiningMacro extends AbstractMacro {
	private static final MiningMacro INSTANCE = new MiningMacro();
	private final BlockMiner miner = BlockMiner.getInstance();
	private MiningSpeedRetrievalTask miningSpeedTask;
	private PickaxeAbilityRetrievalTask pickaxeAbilityTask;
	private State state = State.IDLE;
	private int miningSpeed;
	private boolean restartMining;

	private MiningMacro() {
	}

	public static MiningMacro getInstance() {
		return INSTANCE;
	}

	@Override
	public String getName() {
		return "Mining Macro";
	}

	@Override
	public List<String> getNecessaryItems() {
		return Config.miningTool.isBlank() ? List.of() : List.of(Config.miningTool);
	}

	@Override
	public void onEnable() {
		if (Config.miningTool.isBlank()) {
			disable("Set Config.miningTool before starting the Mining Macro.");
			return;
		}
		if (!FeatureInventory.holdHotbarItem(Minecraft.getInstance(), Config.miningTool)) {
			disable("The configured mining tool is not in the hotbar.");
			return;
		}
		miner.stop();
		AutoGetStats.getInstance().stop();
		miningSpeed = 0;
		restartMining = false;
		miningSpeedTask = new MiningSpeedRetrievalTask();
		pickaxeAbilityTask = new PickaxeAbilityRetrievalTask();
		AutoGetStats.getInstance().startTask(miningSpeedTask);
		AutoGetStats.getInstance().startTask(pickaxeAbilityTask);
		transitionTo(State.GETTING_STATS);
	}

	@Override
	public void onDisable() {
		miner.stop();
		AutoGetStats.getInstance().stop();
		restartMining = false;
		state = State.IDLE;
	}

	@Override
	public void onPause() {
		miner.stop();
		restartMining = state == State.MINING;
	}

	@Override
	public void onResume() {
		if (restartMining) {
			restartMining = false;
			startMining();
		}
	}

	@Override
	public void onTick(net.minecraft.client.Minecraft client) {
		switch (state) {
			case GETTING_STATS -> {
				if (!AutoGetStats.getInstance().hasFinishedAllTasks()) {
					return;
				}
				if (miningSpeedTask.getError() != null) {
					disable("Failed to retrieve mining speed: " + miningSpeedTask.getError());
					return;
				}
				if (pickaxeAbilityTask.getError() != null) {
					disable("Failed to retrieve pickaxe ability: " + pickaxeAbilityTask.getError());
					return;
				}
				Integer result = miningSpeedTask.getResult();
				if (result == null || result <= 0) {
					disable("Mining speed retrieval returned no positive value.");
					return;
				}
				miningSpeed = result;
				startMining();
			}
			case MINING -> handleMiningResult();
			case IDLE -> {
			}
		}
	}

	private void startMining() {
		Map<net.minecraft.world.level.block.Block, Integer> priorities = new LinkedHashMap<>();
		for (MineableBlock type : getConfiguredBlocks()) {
			type.blocks().forEach(block -> priorities.put(block, 1));
		}
		if (priorities.isEmpty()) {
			disable("The selected mining target contains no blocks.");
			return;
		}
		miner.start(priorities, miningSpeed);
		transitionTo(State.MINING);
	}

	private void handleMiningResult() {
		if (miner.isEnabled()) {
			return;
		}
		switch (miner.getError()) {
			case NONE -> {
				if (restartMining) {
					restartMining = false;
					startMining();
				} else {
					disable("Block miner stopped unexpectedly.");
				}
			}
			case NO_POINTS_FOUND -> {
				send("No target point is currently mineable; retrying.");
				startMining();
			}
			case NOT_ENOUGH_BLOCKS -> disable("Not enough target blocks are nearby; move to another vein.");
			case NO_TARGET_BLOCKS -> disable("No target blocks are selected.");
			case STUCK -> disable("Block miner stopped after becoming stuck.");
		}
	}

	private void transitionTo(State next) {
		if (state == next) {
			return;
		}
		reportStateTransition(state.name(), next.name());
		state = next;
	}

	public static List<MineableBlock> getConfiguredBlocks() {
		return switch (Config.oreType) {
			case 0 -> List.of(MineableBlock.GRAY_MITHRIL, MineableBlock.GREEN_MITHRIL,
					MineableBlock.BLUE_MITHRIL, MineableBlock.TITANIUM);
			case 1 -> List.of(MineableBlock.DIAMOND);
			case 2 -> List.of(MineableBlock.EMERALD);
			case 3 -> List.of(MineableBlock.REDSTONE);
			case 4 -> List.of(MineableBlock.LAPIS);
			case 5 -> List.of(MineableBlock.GOLD);
			case 6 -> List.of(MineableBlock.IRON);
			case 7 -> List.of(MineableBlock.COAL);
			default -> List.of();
		};
	}

	private enum State {
		IDLE,
		GETTING_STATS,
		MINING
	}
}
