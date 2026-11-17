package com.example.blockminer.macro.impl.RouteMiner.states;

import com.example.blockminer.feature.impl.AutoGetStats.AutoGetStats;
import com.example.blockminer.feature.impl.AutoGetStats.tasks.impl.MiningSpeedRetrievalTask;
import com.example.blockminer.feature.impl.AutoGetStats.tasks.impl.PickaxeAbilityRetrievalTask;
import com.example.blockminer.macro.impl.RouteMiner.RouteMinerMacro;

public final class GettingStatsState implements RouteMinerState {
	private MiningSpeedRetrievalTask miningSpeedTask;
	private PickaxeAbilityRetrievalTask pickaxeAbilityTask;

	@Override
	public void onStart(RouteMinerMacro macro) {
		AutoGetStats.getInstance().stop();
		miningSpeedTask = new MiningSpeedRetrievalTask();
		pickaxeAbilityTask = new PickaxeAbilityRetrievalTask();
		AutoGetStats.getInstance().startTask(miningSpeedTask);
		AutoGetStats.getInstance().startTask(pickaxeAbilityTask);
	}

	@Override
	public RouteMinerState onTick(RouteMinerMacro macro) {
		AutoGetStats stats = AutoGetStats.getInstance();
		if (!stats.hasFinishedAllTasks()) {
			return this;
		}
		if (miningSpeedTask.getError() != null || pickaxeAbilityTask.getError() != null) {
			macro.disable("Could not retrieve mining stats: "
					+ (miningSpeedTask.getError() != null
					? miningSpeedTask.getError()
					: pickaxeAbilityTask.getError()));
			return this;
		}
		Integer speed = miningSpeedTask.getResult();
		if (speed == null || speed <= 0) {
			macro.disable("Mining speed retrieval returned no positive value.");
			return this;
		}
		macro.setMiningSpeed(speed);
		return new MovingState();
	}

	@Override
	public void onEnd(RouteMinerMacro macro) {
		AutoGetStats.getInstance().stop();
	}
}
